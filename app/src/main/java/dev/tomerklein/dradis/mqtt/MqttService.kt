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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ProcessLifecycleOwner
import dev.tomerklein.dradis.BuildConfig
import dev.tomerklein.dradis.MainActivity
import dev.tomerklein.dradis.R
import dev.tomerklein.dradis.ServiceLocator
import dev.tomerklein.dradis.commands.AndroidCustomTopicEffects
import dev.tomerklein.dradis.commands.CmdHandler
import dev.tomerklein.dradis.commands.CommandRouter
import dev.tomerklein.dradis.commands.CommandSink
import dev.tomerklein.dradis.commands.CustomTopicProcessor
import dev.tomerklein.dradis.commands.GetStatusHandler
import dev.tomerklein.dradis.commands.LocationHandler
import dev.tomerklein.dradis.commands.NotifyHandler
import dev.tomerklein.dradis.commands.PhotoHandler
import dev.tomerklein.dradis.commands.PingHandler
import dev.tomerklein.dradis.commands.SayHandler
import dev.tomerklein.dradis.commands.SmsHandler
import dev.tomerklein.dradis.commands.TtsSpeaker
import dev.tomerklein.dradis.log.LogFormatter
import dev.tomerklein.dradis.net.NetworkMonitor
import dev.tomerklein.dradis.settings.ClientId
import dev.tomerklein.dradis.settings.CustomTopic
import dev.tomerklein.dradis.settings.OutboundTopic
import dev.tomerklein.dradis.settings.OutboundTopicValidator
import dev.tomerklein.dradis.telemetry.AndroidDeviceValues
import dev.tomerklein.dradis.telemetry.CustomOutboundPublisher
import dev.tomerklein.dradis.settings.CustomTopicType
import dev.tomerklein.dradis.settings.DradisSettings
import dev.tomerklein.dradis.telemetry.LocationPublisher
import dev.tomerklein.dradis.telemetry.PeriodicReporter
import dev.tomerklein.dradis.telemetry.SensorReporter
import dev.tomerklein.dradis.telemetry.TelemetryReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.concurrent.Semaphore

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
    // Keeps the Wi-Fi radio + CPU awake so the link survives device sleep.
    private val connectionLocks by lazy { ConnectionLocks(this) }

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
    private lateinit var customProcessor: CustomTopicProcessor
    @Volatile
    private var customTopics: List<CustomTopic> = emptyList()
    // Custom filters currently subscribed on the live client; guarded by customLock
    // because onConnected runs on a transport thread while changes run on connScope.
    private var subscribedCustom: Set<String> = emptySet()
    private val customLock = Any()
    private val pictureGate = Semaphore(1)
    private val dupGuard = DuplicateGuard()
    private lateinit var outboundPublisher: CustomOutboundPublisher
    @Volatile
    private var customOutboundRaw: List<OutboundTopic> = emptyList()
    // Topics we published on recently (HA discovery etc.), so discovery never lists our own echoes.
    private val ownPublished: MutableSet<String> = java.util.Collections.newSetFromMap(
        java.util.Collections.synchronizedMap(object : LinkedHashMap<String, Boolean>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>) = size > 500
        }),
    )
    private val hassDiscovery = HassDiscovery(this)
    private var reselectJob: Job? = null
    private var ssidRetryJob: Job? = null
    private var authRetryJob: Job? = null

    // Bumped on every (re)connect. A superseded client's async connect/disconnect
    // listeners carry the generation they were created with; if it no longer
    // matches, their callbacks are ignored — otherwise the OLD client's late
    // "disconnected" callback clobbers the NEW client's "connected" status, so
    // the UI shows Disconnected while commands still flow.
    @Volatile
    private var connGen = 0

    // Gates the first broker selection until the persisted last-known SSID has
    // been seeded into the NetworkMonitor, so a cold start picks the correct
    // broker straight away instead of briefly connecting to WAN on a not-yet-
    // resolved (null) SSID and then flipping to LAN.
    @Volatile
    private var seeded = false

    // Re-resolve the SSID when the app is brought to the foreground (e.g. right
    // after the user grants location permission), so we don't stay on WAN when
    // we're actually on a home Wi-Fi whose SSID wasn't readable at first.
    private val appForegroundObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_START) nudgeSsidResolution()
    }

    // --- CommandSink ---------------------------------------------------------
    override val appContext: Context get() = applicationContext
    override val topics: Topics get() = currentTopics
    override val settings: DradisSettings get() = current
    override val currentSsid: String?
        get() = if (::networkMonitor.isInitialized) networkMonitor.currentSsid() else null
    override val onWifi: Boolean
        get() = ::networkMonitor.isInitialized && networkMonitor.isWifi()

    override fun publish(topic: String, payload: String, retain: Boolean) {
        ownPublished += topic
        client?.publish(topic, payload, retain)
        // Cap what the in-app buffer keeps: a base64 photo would otherwise sit
        // in memory (and in log dumps) at full size.
        mqttLog.outbound(topic, payload.take(MAX_LOGGED_PAYLOAD), now())
    }

    override fun publish(topic: String, payload: ByteArray, retain: Boolean) {
        ownPublished += topic
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
        // Hold the radio/CPU awake so the link survives device sleep. Acquired
        // here (default on) so it doesn't depend on the settings collector's path;
        // the collector releases it if the user turns the setting off.
        connectionLocks.acquire()

        telemetryReporter = TelemetryReporter(this)
        telemetryReporter.start()
        sensorReporter = SensorReporter(this)
        sensorReporter.start()
        ttsSpeaker = TtsSpeaker(this)
        outboundPublisher = CustomOutboundPublisher(this, AndroidDeviceValues(telemetryReporter, this))
        periodicReporter = PeriodicReporter(this, ioScope, telemetryReporter, sensorReporter) {
            if (client?.isConnected == true) outboundPublisher.publishAll(safeOutbound())
        }

        val publishReport = {
            telemetryReporter.publishAll()
            if (current.sensorsEnabled) sensorReporter.publish()
        }
        router = CommandRouter(
            sink = this,
            handlers = listOf(
                SmsHandler(),
                LocationHandler(),
                PingHandler(),
                PhotoHandler(),
                NotifyHandler(ttsSpeaker),
                SayHandler(ttsSpeaker),
                GetStatusHandler(onReport = publishReport),
                CmdHandler(
                    mqttLog = mqttLog,
                    onHeartbeat = publishReport,
                    onReconnect = { connScope.launch { selection?.let { reconnect(it) } } },
                    // Rebuild the client and re-announce everything: the same
                    // work a settings change triggers.
                    onRestart = {
                        connScope.launch {
                            hassDiscovery.publish()
                            periodicReporter.restart()
                            selection?.let { reconnect(it) }
                        }
                    },
                ),
            ),
        )

        customProcessor = CustomTopicProcessor(
            effects = AndroidCustomTopicEffects(
                context = this,
                tts = ttsSpeaker,
                settings = { current },
                pictures = ServiceLocator.pictures,
                log = ::logInfo,
            ),
            _values = ServiceLocator.customTopicValues,
        )

        networkMonitor = NetworkMonitor(
            context = this,
            // Persist each freshly-read SSID so the next process start (the
            // service is restarted aggressively by the OS) re-picks the home
            // broker immediately instead of starting blind (null → WAN).
            onSsidResolved = { ssid -> connScope.launch { settingsRepo.setLastKnownSsid(ssid) } },
            onChange = { onNetworkChanged() },
        )
        networkMonitor.start()
        // Seed the last-known SSID from a previous run so we don't flap to WAN on
        // a cold start before the (often slow) first SSID read resolves. The first
        // broker selection is gated on [seeded] (see applySelection) so it goes
        // straight to the correct broker instead of briefly touching WAN.
        connScope.launch {
            try {
                networkMonitor.seedSsid(settingsRepo.lastKnownSsid())
            } finally {
                seeded = true
                scheduleReselect(debounceMs = 0)
            }
        }
        // Cold start: the SSID often isn't readable the instant the network
        // appears (WifiInfo not populated yet / permission just granted), which
        // would wrongly pick WAN on home Wi-Fi. Retry a few times, and re-check
        // whenever the app is foregrounded.
        ProcessLifecycleOwner.get().lifecycle.addObserver(appForegroundObserver)
        nudgeSsidResolution()

        connScope.launch { settingsRepo.migrate() }
        connScope.launch { runDiscoverySessions() }
        connScope.launch {
            ServiceLocator.customTopics.outbound.collect { list ->
                customOutboundRaw = list
                // Publish right away when the list changes on a live link; on connect it is sent by onConnected.
                if (client?.isConnected == true) ioScope.launch { outboundPublisher.publishAll(safeOutbound()) }
                syncCustomSubscriptions()
            }
        }
        connScope.launch {
            ServiceLocator.customTopics.topics.collect { list ->
                customTopics = list
                customProcessor.retain(list.map { it.id }.toSet())
                syncCustomSubscriptions()
            }
        }
        connScope.launch {
            // Load the stored topic lists before the first connect, so the echo guards are in force
            // when subscriptions are built (the collectors above may not have emitted yet).
            customTopics = ServiceLocator.customTopics.topics.first()
            customOutboundRaw = ServiceLocator.customTopics.outbound.first()
            settingsRepo.settings.collect { s ->
                // Ensure a stable, per-device client id before connecting. Generate
                // one at first setup, AND regenerate a transfer-cloned id (settings
                // copied onto another device) — otherwise the two installs share a
                // client id and evict each other in an endless reconnect loop (often
                // surfacing as NOT_AUTHORIZED). Wait for the re-emission with the
                // corrected id rather than connecting now.
                val fingerprint = ClientId.deviceFingerprint(this@MqttService)
                val clonedId = fingerprint != null && s.clientIdDevice != fingerprint
                if (s.clientId.isBlank() || clonedId) {
                    settingsRepo.ensureClientId()
                    return@collect
                }
                current = s
                currentTopics = Topics(s.topicPrefix, s.deviceName)
                // Keep the radio/CPU awake so the link survives sleep (or release
                // the locks if the user turned it off to save battery).
                if (s.keepAliveWhileLocked) connectionLocks.acquire() else connectionLocks.release()
                periodicReporter.restart()
                hassDiscovery.publish()
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
        ssidRetryJob?.cancel()
        authRetryJob?.cancel()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appForegroundObserver)
        networkMonitor.stop()
        periodicReporter.stop()
        telemetryReporter.stop()
        sensorReporter.stop()
        ttsSpeaker.shutdown()
        ServiceLocator.discovery.stop()
        client?.disconnect()
        client = null
        connectionLocks.release()
        connScope.cancel()
        ioScope.cancel()
        ServiceLocator.updateConnection(ConnectionStatus())
        super.onDestroy()
    }

    /** Re-read the SSID a few times (with backoff) until it resolves or we leave
     *  Wi-Fi. Each pass fires [onNetworkChanged] → reselect, so the broker flips
     *  from WAN to LAN as soon as a home SSID becomes readable. */
    private fun nudgeSsidResolution() {
        if (!::networkMonitor.isInitialized) return
        ssidRetryJob?.cancel()
        ssidRetryJob = connScope.launch {
            repeat(SSID_RETRY_ATTEMPTS) {
                networkMonitor.reevaluate()
                if (!networkMonitor.isWifi() || networkMonitor.currentSsid() != null) return@launch
                delay(SSID_RETRY_DELAY_MS)
            }
        }
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
        // Hold off the very first selection until the seed has been applied (see
        // onCreate); the seed coroutine re-triggers this once ready. Network and
        // settings callbacks that arrive earlier are no-ops here.
        if (!seeded) return
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

    /** After an auth rejection the fast reconnect loop is cancelled (see
     *  [MqttClientWrapper]); retry slowly so a temporary broker lockout can clear
     *  without hammering — and without spinning on genuinely-bad credentials. */
    private fun scheduleAuthRetry() {
        authRetryJob?.cancel()
        authRetryJob = connScope.launch {
            delay(AUTH_RETRY_MS)
            selection?.let { reconnect(it) }
        }
    }

    private fun reconnect(sel: BrokerSelector.Selection) {
        authRetryJob?.cancel()
        client?.disconnect()
        // New generation: the just-disconnected client's late listeners (and any
        // earlier one's) will no longer match and are ignored.
        val gen = ++connGen
        logInfo("Selecting ${sel.kind} broker ${sel.config.host}:${sel.config.port} (ssid=${sel.ssid ?: "none"})")

        // Stable per-device id (see ClientId), generated/persisted by the settings
        // collector above. Fallback only if a connect races ahead of first-run
        // persistence — normally never blank here.
        val baseId = current.clientId.ifBlank { ClientId.generate() }
        // Distinct client id per broker (…-lan / …-wan) so the LAN and WAN
        // sessions never share an id (e.g. when both brokers are bridged or are
        // the same broker reached two ways).
        val clientId = "$baseId-${sel.kind.name.lowercase()}"
        logInfo("MQTT client id: $clientId")
        val wrapper = MqttClientWrapper(
            clientId = clientId,
            onMessage = { topic, bytes, retained -> onInbound(topic, bytes, retained) },
            onConnected = { if (gen == connGen) onConnected() },
            onStateChange = { state ->
                if (gen == connGen) {
                    updateStatus(state, selection, state.name)
                    if (state == ConnState.UNAUTHORIZED) scheduleAuthRetry()
                }
            },
        )
        client = wrapper
        updateStatus(ConnState.CONNECTING, sel, "Connecting to ${sel.config.host}")
        wrapper.connect(sel.config, currentTopics.status)
    }

    /** Fires on every (re)connect: re-subscribe and republish retained state. */
    private fun onConnected() {
        val c = client ?: return
        val t = currentTopics
        val custom = synchronized(customLock) { enabledCustomFilters().also { subscribedCustom = it } }
        c.subscribe(t.inboundTopics() + custom)
        c.publish(t.status, "1", retain = true)
        c.publish(t.version, BuildConfig.DRADIS_VERSION, retain = true)
        hassDiscovery.publish()
        // Send a full report immediately on connect — first-save connect and
        // every reconnect (incl. after a network change) — so subscribers get
        // fresh state without waiting for the next update-interval tick.
        if (current.telemetryEnabled) telemetryReporter.publishAll()
        if (current.sensorsEnabled) sensorReporter.publish()
        if (current.locationEnabled) ioScope.launch { LocationPublisher.publishCurrent(this@MqttService) }
        ioScope.launch { outboundPublisher.publishAll(safeOutbound()) }
        logInfo("Connected; subscribed to ${t.inboundTopics().size} topics")
        updateStatus(ConnState.CONNECTED, selection, "Connected")
    }

    private fun onInbound(topic: String, bytes: ByteArray, retained: Boolean) {
        val discovery = ServiceLocator.discovery
        // Anything the app does not itself handle can only arrive through a discovery
        // subscription (possibly still draining after the session ended). It is recorded
        // while a session runs and otherwise dropped: never routed, never logged.
        if (!handledByApp(topic)) {
            val own = topic in currentTopics.outboundTopics() || topic in ownPublished
            if (!own && discovery.isActive) discovery.record(topic, bytes, retained)
            return
        }
        // A discovery subscription overlaps the app's own, so the broker may deliver
        // the same publish twice; a command must not run twice.
        if (discovery.isActive && dupGuard.isDuplicate(topic, bytes)) return
        // Commands are events, not state. Ignore retained messages delivered from
        // the broker's store on (re)subscribe so a leftover/retained command never
        // re-fires (e.g. a retained-clear or an accidentally retained publish).
        if (retained) {
            mqttLog.inbound(redactPii(topic), "(retained command ignored)", now())
            return
        }
        if (routeToCustomTopics(topic, bytes)) return
        val text = bytes.toString(StandardCharsets.UTF_8)
        // The handler gets the full payload; only the in-app log is redacted +
        // length-capped so PII (phone numbers, SMS text) isn't retained verbatim.
        mqttLog.inbound(redactPii(topic), redactPii(text).take(MAX_LOGGED_PAYLOAD), now())
        ioScope.launch { router.handle(topic, text) }
    }

    private fun handledByApp(topic: String): Boolean =
        currentTopics.inboundTopics().any { TopicFilter.matches(it, topic) } ||
            activeCustomTopics().any { TopicFilter.matches(it.filter, topic) }

    /** Subscribes to each discovery session's filter and ends it at its deadline, on
     *  disconnect, or when the UI stops it. Sessions never outlive the connection. */
    private suspend fun runDiscoverySessions() {
        ServiceLocator.discovery.session.collectLatest { s ->
            if (s == null) return@collectLatest
            val c = client?.takeIf { it.isConnected }
            if (c == null) {
                ServiceLocator.discovery.stop()
                return@collectLatest
            }
            // Never touch a subscription the app already owns under the same filter.
            val owned = s.filter in currentTopics.inboundTopics() || s.filter in synchronized(customLock) { subscribedCustom }
            if (!owned) c.subscribe(listOf(s.filter))
            logInfo("Discovery started on ${redactPii(s.filter)}")
            try {
                delay((s.deadlineMillis - System.currentTimeMillis()).coerceAtLeast(0))
                ServiceLocator.discovery.stop()
            } finally {
                // Re-check: a custom topic imported mid-session may now own this filter.
                val ownedNow = s.filter in currentTopics.inboundTopics() ||
                    s.filter in synchronized(customLock) { subscribedCustom }
                if (!owned && !ownedNow) client?.unsubscribe(listOf(s.filter))
            }
        }
    }

    /** Enabled custom topics that are safe to subscribe: a filter overlapping a
     *  built-in command topic or one the app publishes on (e.g. after a prefix
     *  change made a stored filter collide) would get duplicate deliveries or
     *  the app's own telemetry echoed back, so it is skipped. */
    /** Stored outbound topics that are still safe under the current prefix, device name and custom filters. */
    private fun safeOutbound(): List<OutboundTopic> = OutboundTopicValidator.safe(
        customOutboundRaw,
        currentTopics.inboundTopics() + currentTopics.outboundTopics(),
        customTopics.map { it.filter },
        "${current.topicPrefix}/",
    )

    private fun activeCustomTopics(): List<CustomTopic> {
        // Custom outbound topics count as our own: a filter covering one would echo it back.
        val own = currentTopics.inboundTopics() + currentTopics.outboundTopics() + customOutboundRaw.map { it.topic }
        return customTopics.filter { t -> t.enabled && own.none { TopicFilter.overlaps(it, t.filter) } }
    }

    private fun enabledCustomFilters(): Set<String> = activeCustomTopics().map { it.filter }.toSet()

    /** Applies a change to the custom topic list to the live client: subscribe the
     *  new filters, unsubscribe the removed ones. Not connected: [onConnected]
     *  subscribes the full set instead. */
    private fun syncCustomSubscriptions() {
        synchronized(customLock) {
            val c = client?.takeIf { it.isConnected } ?: return
            val want = enabledCustomFilters()
            // Never drop a built-in subscription that happens to share a filter.
            val builtIn = currentTopics.inboundTopics().toSet()
            c.subscribe((want - subscribedCustom).toList())
            c.unsubscribe((subscribedCustom - want - builtIn).toList())
            subscribedCustom = want
        }
    }

    /** Hands [topic] to the enabled custom topics it matches. Built-in command
     *  topics always win. Returns true if the message was consumed here. */
    private fun routeToCustomTopics(topic: String, bytes: ByteArray): Boolean {
        if (currentTopics.inboundTopics().any { TopicFilter.matches(it, topic) }) return false
        val matched = activeCustomTopics().filter { TopicFilter.matches(it.filter, topic) }
        if (matched.isEmpty()) return false
        // Redact, then cut (as the built-in path does) so a digit run straddling the
        // cut is still masked. Pictures are binary: log only their size.
        val preview = if (matched.all { it.type == CustomTopicType.PICTURE }) {
            "<${bytes.size} bytes>"
        } else {
            val head = String(bytes, 0, minOf(bytes.size, MAX_LOGGED_PAYLOAD * 4), StandardCharsets.UTF_8)
            redactPii(head).take(MAX_LOGGED_PAYLOAD)
        }
        mqttLog.inbound(redactPii(topic), preview, now())
        ioScope.launch {
            for (t in matched) {
                // One picture at a time: decoding a large payload is memory-heavy, so
                // a burst is dropped rather than queued.
                val gated = t.type == CustomTopicType.PICTURE
                if (gated && !pictureGate.tryAcquire()) {
                    logInfo("${t.name}: busy, picture dropped")
                    continue
                }
                try {
                    customProcessor.process(t, bytes, current) { logInfo(it) }
                } catch (e: Exception) {
                    logInfo("Error processing custom topic ${t.name}: ${e.message}")
                } finally {
                    if (gated) pictureGate.release()
                }
            }
        }
        return true
    }

    /** Inbound entries are redacted in the in-app log; outbound ones stay intact
     *  there (coordinates) and are redacted only when dumped by `send_logs`. */
    private fun redactPii(s: String): String = LogFormatter.redactPii(s)

    // --- Notification + status ----------------------------------------------
    private fun updateStatus(state: ConnState, sel: BrokerSelector.Selection?, detail: String) {
        // Sessions are clean-session subscriptions: they do not survive a reconnect.
        if (state != ConnState.CONNECTED) ServiceLocator.discovery.stop()
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
            ConnState.UNAUTHORIZED -> "Not authorized ($brokerLabel) — check broker credentials"
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
        private const val MAX_LOGGED_PAYLOAD = 300
        // SSID can lag the network coming up; retry resolution a handful of times.
        private const val SSID_RETRY_ATTEMPTS = 5
        private const val SSID_RETRY_DELAY_MS = 1500L
        // Slow retry after the broker rejects auth, so a temporary lockout can
        // clear without the app hammering it (which keeps fail2ban-style bans alive).
        private const val AUTH_RETRY_MS = 60_000L

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
