package dev.tomerklein.dradis

import android.content.Context
import dev.tomerklein.dradis.commands.PictureStore
import dev.tomerklein.dradis.commands.ReceivedValue
import dev.tomerklein.dradis.log.MqttLog
import dev.tomerklein.dradis.mqtt.ConnectionStatus
import dev.tomerklein.dradis.settings.CustomTopicsRepository
import dev.tomerklein.dradis.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Minimal manual service locator (Hilt is intentionally skipped for an app this
 * size — see CLAUDE.md §3). Initialised once from [DradisApp.onCreate].
 */
object ServiceLocator {
    lateinit var settings: SettingsRepository
        private set
    lateinit var mqttLog: MqttLog
        private set
    lateinit var customTopics: CustomTopicsRepository
        private set
    lateinit var pictures: PictureStore
        private set

    /** Latest value per custom-topic id, written by the service, read by the UI. */
    val customTopicValues = MutableStateFlow<Map<String, ReceivedValue>>(emptyMap())

    private val _connection = MutableStateFlow(ConnectionStatus())
    /** Live MQTT connection state, observed by the Status screen. */
    val connection: StateFlow<ConnectionStatus> = _connection

    fun init(context: Context) {
        val app = context.applicationContext
        settings = SettingsRepository(app)
        mqttLog = MqttLog()
        customTopics = CustomTopicsRepository(app)
        pictures = PictureStore(app)
    }

    fun updateConnection(status: ConnectionStatus) {
        _connection.value = status
    }
}
