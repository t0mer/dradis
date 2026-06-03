package dev.tomerklein.dradis.mqtt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import dev.tomerklein.dradis.BuildConfig
import dev.tomerklein.dradis.MainActivity
import dev.tomerklein.dradis.R
import dev.tomerklein.dradis.ServiceLocator
import dev.tomerklein.dradis.commands.CommandRouter
import dev.tomerklein.dradis.commands.CommandSink
import dev.tomerklein.dradis.commands.GetStatusHandler
import dev.tomerklein.dradis.commands.LocationHandler
import dev.tomerklein.dradis.commands.NotifyHandler
import dev.tomerklein.dradis.commands.PhotoHandler
import dev.tomerklein.dradis.commands.PingHandler
import dev.tomerklein.dradis.commands.SayHandler
import dev.tomerklein.dradis.commands.SmsHandler
import dev.tomerklein.dradis.commands.TtsSpeaker
import dev.tomerklein.dradis.net.NetworkMonitor
import dev.tomerklein.dradis.settings.DradisSettings
import dev.tomerklein.dradis.telemetry.PeriodicReporter
import dev.tomerklein.dradis.telemetry.SensorReporter
import dev.tomerklein.dradis.telemetry.TelemetryReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

private const val TAG = "dradis.svc"

/**
 * Foreground service that owns the persistent MQTT connection for the whole app
 * lifetime (CLAUDE.md §9.1). Observes settings + connectivity, picks the LAN or
 * WAN broker via [BrokerSelector], (re)connects on change, and routes inbound
 * commands through [CommandRouter].
 */
class MqttService : LifecycleService(), CommandSink {

    private val settingsRepo by lazy { ServiceLocator.settings }
    private val mqttLog by lazy { ServiceLocator.mqttLog }

    @Volatile
    private var current: DradisSettings = DradisSettings()
    @Volatile
    private var currentTopics: Topics = Topics(current.topicPrefix, current.deviceName)

    @Volatile
    private var client: MqttClientWrapper? = null
    @Volatile
    private var selection: BrokerSelector.Selection? = null

    // Connection management runs serially OFF the main thread (building/closing
    // the HiveMQ/Netty client must never block the UI/service main thread).
    private val connScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    // Inbound command handlers run here so a slow handler can't stall the link.
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var router: CommandRouter
    private lateinit var telemetryReporter: TelemetryReporter
    private lateinit var sensorReporter: SensorReporter
    private lateinit var periodicReporter: PeriodicReporter
    private lateinit var ttsSpeaker: TtsSpeaker
    private var reselectJob: Job? = null

    // --- CommandSink ---------------------------------------------------------
    override val appContext: Context get() = applicationContext
    override val topics: Topics get() = currentTopics
    override val settings: DradisSettings get() = current
    override val currentSsid: String?
        get() = if (::networkMonitor.isInitialized) networkMonitor.currentSsid() else null
    override val onWifi: Boolean
        get() = ::networkMonitor.isInitialized && networkMonitor.isWifi()

    override fun publish(topic: String, payload: String, retain: Boolean) {
        client?.publish(topic, payload, retain)
        mqttLog.outbound(topic, payload, now())
    }

    override fun publish(topic: String, payload: ByteArray, retain: Boolean) {
        client?.publish(topic, payload, retain = retain)
        mqttLog.outbound(topic, "<${payload.size} bytes>", now())
    }

    override fun logInfo(message: String) {
        mqttLog.info(message, now())
    }

    // --- Lifecycle -----------------------------------------------------------
    override fun onCreate() {
        super.onCreate()
        startInForeground("Starting…")

        telemetryReporter = TelemetryReporter(this)
        telemetryReporter.start()
        sensorReporter = SensorReporter(this)
        sensorReporter.start()
        ttsSpeaker = TtsSpeaker(this)
        periodicReporter = PeriodicReporter(this, ioScope, telemetryReporter, sensorReporter)

        router = CommandRouter(
            sink = this,
            handlers = listOf(
                SmsHandler(),
                LocationHandler(),
                PingHandler(),
                PhotoHandler(),
                NotifyHandler(ttsSpeaker),
                SayHandler(ttsSpeaker),
                GetStatusHandler(onReport = {
                    telemetryReporter.publishAll()
                    if (current.sensorsEnabled) sensorReporter.publish()
                }),
            ),
        )

        networkMonitor = NetworkMonitor(this) { onNetworkChanged() }
        networkMonitor.start()

        connScope.launch { settingsRepo.migrate() }
        connScope.launch {
            settingsRepo.settings.collect { s ->
                current = s
                currentTopics = Topics(s.topicPrefix, s.deviceName)
                periodicReporter.restart()
                scheduleReselect(debounceMs = 0)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        reselectJob?.cancel()
        networkMonitor.stop()
        periodicReporter.stop()
        telemetryReporter.stop()
        sensorReporter.stop()
        ttsSpeaker.shutdown()
        client?.disconnect()
        client = null
        connScope.cancel()
        ioScope.cancel()
        ServiceLocator.updateConnection(ConnectionStatus())
        super.onDestroy()
    }

    // --- Connection management ----------------------------------------------
    private fun onNetworkChanged() {
        if (current.reconnectOnNetworkChange) scheduleReselect(debounceMs = DEBOUNCE_MS)
        else scheduleReselect(debounceMs = 0)
    }

    /** Debounce rapid network flaps before re-evaluating the broker (§7).
     *  Runs on connScope (serial, off the main thread). */
    private fun scheduleReselect(debounceMs: Long) {
        reselectJob?.cancel()
        reselectJob = connScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            applySelection()
        }
    }

    private fun applySelection() {
        val ssid = networkMonitor.currentSsid()
        val sel = BrokerSelector.select(current, ssid)

        if (!sel.config.isConfigured) {
            client?.disconnect()
            client = null
            selection = sel
            updateStatus(ConnState.DISCONNECTED, sel, "Broker not configured")
            return
        }

        val prev = selection
        val brokerChanged = prev == null ||
            prev.kind != sel.kind ||
            prev.config != sel.config
        selection = sel

        // Only (re)build the client when the selected broker actually changes
        // (or none exists yet). When it's the same broker, HiveMQ's automatic
        // reconnect handles connectivity — rebuilding here on every network tick
        // would leak Netty thread pools and ANR the process.
        if (brokerChanged || client == null) {
            reconnect(sel)
        } else {
            val connected = client?.isConnected == true
            updateStatus(
                if (connected) ConnState.CONNECTED else ConnState.CONNECTING,
                sel,
                if (connected) "Connected" else "Connecting…",
            )
        }
    }

    private fun reconnect(sel: BrokerSelector.Selection) {
        client?.disconnect()
        logInfo("Selecting ${sel.kind} broker ${sel.config.host}:${sel.config.port} (ssid=${sel.ssid ?: "none"})")

        val clientId = "dradis-${current.deviceName}-${BuildConfig.DRADIS_VERSION}"
        val wrapper = MqttClientWrapper(
            clientId = clientId,
            onMessage = { topic, bytes -> onInbound(topic, bytes) },
            onConnected = { onConnected() },
            onStateChange = { state -> updateStatus(state, selection, state.name) },
        )
        client = wrapper
        updateStatus(ConnState.CONNECTING, sel, "Connecting to ${sel.config.host}")
        wrapper.connect(sel.config, currentTopics.status)
    }

    /** Fires on every (re)connect: re-subscribe and republish retained state. */
    private fun onConnected() {
        val c = client ?: return
        val t = currentTopics
        c.subscribe(t.inboundTopics())
        c.publish(t.status, "1", retain = true)
        c.publish(t.version, BuildConfig.DRADIS_VERSION, retain = true)
        if (current.telemetryEnabled) telemetryReporter.publishAll()
        if (current.sensorsEnabled) sensorReporter.publish()
        logInfo("Connected; subscribed to ${t.inboundTopics().size} topics")
        updateStatus(ConnState.CONNECTED, selection, "Connected")
    }

    private fun onInbound(topic: String, bytes: ByteArray) {
        val text = bytes.toString(StandardCharsets.UTF_8)
        mqttLog.inbound(topic, text, now())
        ioScope.launch { router.handle(topic, text) }
    }

    // --- Notification + status ----------------------------------------------
    private fun updateStatus(state: ConnState, sel: BrokerSelector.Selection?, detail: String) {
        val status = ConnectionStatus(
            state = state,
            broker = sel?.kind ?: BrokerKind.NONE,
            ssid = sel?.ssid,
            host = sel?.config?.host,
            detail = detail,
        )
        ServiceLocator.updateConnection(status)
        val brokerLabel = when (status.broker) {
            BrokerKind.LAN -> "LAN"
            BrokerKind.WAN -> "WAN"
            BrokerKind.NONE -> "—"
        }
        val text = when (state) {
            ConnState.CONNECTED -> "Connected ($brokerLabel · ${status.host})"
            ConnState.CONNECTING -> "Connecting ($brokerLabel)…"
            ConnState.DISCONNECTED -> detail.ifBlank { "Disconnected" }
        }
        updateNotification(text)
    }

    private fun startInForeground(status: String) {
        ensureChannel()
        // Start with the always-allowed specialUse type only. location/camera FGS
        // types require their runtime permissions to be held at start time
        // (Android 14+), so we keep the persistent link on specialUse and let
        // CameraX/Location use while-in-use access when a command runs.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(status), type)
    }

    private fun updateNotification(status: String) {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun buildNotification(status: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.fgs_notification_title))
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.fgs_channel_name),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { setShowBadge(false) }
                )
            }
        }
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        private const val CHANNEL_ID = "dradis_connection"
        private const val NOTIFICATION_ID = 1001
        private const val DEBOUNCE_MS = 2500L

        fun start(context: Context) {
            val intent = Intent(context, MqttService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
