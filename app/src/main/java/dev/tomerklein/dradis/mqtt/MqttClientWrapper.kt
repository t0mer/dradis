package dev.tomerklein.dradis.mqtt

import android.util.Log
import com.hivemq.client.mqtt.MqttClientSslConfig
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import dev.tomerklein.dradis.settings.BrokerConfig
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

private const val TAG = "dradis.mqtt"

/**
 * Thin wrapper over the HiveMQ MQTT 3.1.1 async client (CLAUDE.md §3): connect
 * with LWT, auto-reconnect, subscribe and publish. Re-subscription and the
 * retained `status=1` republish are driven by [onConnected], which fires on
 * every (re)connection.
 */
class MqttClientWrapper(
    private val clientId: String,
    private val onMessage: (topic: String, payload: ByteArray, retained: Boolean) -> Unit,
    private val onConnected: () -> Unit,
    private val onStateChange: (ConnState) -> Unit,
) {
    private var client: Mqtt3AsyncClient? = null

    val isConnected: Boolean get() = client?.state?.isConnected == true

    fun connect(config: BrokerConfig, lwtTopic: String) {
        disconnect()
        onStateChange(ConnState.CONNECTING)

        val c = Mqtt3Client.builder()
            .identifier(clientId)
            .serverHost(config.host)
            .serverPort(config.port)
            // Default backoff caps at 2 min, which makes reconnects feel stuck.
            // Retry quickly and cap the wait so we recover within ~20s.
            .automaticReconnect()
            .initialDelay(1, TimeUnit.SECONDS)
            .maxDelay(20, TimeUnit.SECONDS)
            .applyAutomaticReconnect()
            .apply {
                if (config.tls) {
                    val tmf = TlsCerts.trustManagerFactory(config.caCert)
                    if (tmf != null) {
                        // Trust the user-supplied CA (self-signed / private CA).
                        sslConfig(MqttClientSslConfig.builder().trustManagerFactory(tmf).build())
                    } else {
                        // No/!invalid CA supplied → system trust store (public CAs).
                        sslWithDefaultConfig()
                    }
                }
            }
            .addConnectedListener {
                Log.i(TAG, "connected id=$clientId ${config.host}:${config.port}")
                onStateChange(ConnState.CONNECTED)
                onConnected()
            }
            .addDisconnectedListener { ctx ->
                // source=SERVER => broker closed us (e.g. another client with the
                // same id took over); source=CLIENT => local/network error.
                Log.i(TAG, "disconnected id=$clientId source=${ctx.source} cause=${ctx.cause}")
                onStateChange(ConnState.DISCONNECTED)
            }
            .buildAsync()
        client = c

        // Register the global inbound callback before connecting.
        c.publishes(MqttGlobalPublishFilter.ALL) { publish ->
            runCatching {
                onMessage(publish.topic.toString(), publish.payloadAsBytes, publish.isRetain)
            }.onFailure { Log.e(TAG, "inbound dispatch failed", it) }
        }

        val connect = c.connectWith()
            // Shorter keep-alive detects dead links faster and keeps NAT/firewall
            // mappings alive on cellular (which often time out around 30-60s).
            .keepAlive(45)
            .cleanSession(true)
        if (config.username.isNotBlank()) {
            connect.simpleAuth()
                .username(config.username)
                .password(config.password.toByteArray(StandardCharsets.UTF_8))
                .applySimpleAuth()
        }
        connect.willPublish()
            .topic(lwtTopic)
            .payload("0".toByteArray(StandardCharsets.UTF_8))
            .qos(MqttQos.AT_LEAST_ONCE)
            .retain(true)
            .applyWillPublish()
            .send()
            .whenComplete { _, err ->
                if (err != null) Log.e(TAG, "connect to ${config.host}:${config.port} failed", err)
            }
    }

    fun subscribe(topics: List<String>) {
        val c = client ?: return
        topics.forEach { topic ->
            c.subscribeWith()
                .topicFilter(topic)
                .qos(MqttQos.AT_LEAST_ONCE)
                .send()
                .whenComplete { _, err ->
                    if (err != null) Log.e(TAG, "subscribe $topic failed", err)
                }
        }
    }

    fun publish(
        topic: String,
        payload: ByteArray,
        qos: MqttQos = MqttQos.AT_LEAST_ONCE,
        retain: Boolean = false,
    ) {
        val c = client ?: return
        c.publishWith()
            .topic(topic)
            .payload(payload)
            .qos(qos)
            .retain(retain)
            .send()
            .whenComplete { _, err ->
                if (err != null) Log.e(TAG, "publish $topic failed", err)
            }
    }

    fun publish(topic: String, payload: String, retain: Boolean = false) =
        publish(topic, payload.toByteArray(StandardCharsets.UTF_8), retain = retain)

    fun disconnect() {
        client?.let { c ->
            runCatching { c.disconnect() }
        }
        client = null
    }
}
