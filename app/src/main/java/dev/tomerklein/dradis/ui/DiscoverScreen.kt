package dev.tomerklein.dradis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.tomerklein.dradis.ServiceLocator
import dev.tomerklein.dradis.commands.DiscoveredTopic
import dev.tomerklein.dradis.commands.TopicDiscovery
import dev.tomerklein.dradis.mqtt.ConnState
import dev.tomerklein.dradis.mqtt.Topics
import dev.tomerklein.dradis.settings.CustomTopic
import dev.tomerklein.dradis.settings.CustomTopicType
import dev.tomerklein.dradis.settings.DradisSettings
import dev.tomerklein.dradis.ui.MonoStyle
import dev.tomerklein.dradis.ui.components.ConsoleCard
import dev.tomerklein.dradis.ui.components.Field
import dev.tomerklein.dradis.ui.components.MonoText
import dev.tomerklein.dradis.ui.components.ToggleRow
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Passive topic discovery: listen on a filter for a short, user-chosen time and
 * offer each topic seen as an inbound custom topic.
 */
@Composable
fun DiscoverScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val discovery = ServiceLocator.discovery
    val settings by ServiceLocator.settings.settings.collectAsState(initial = DradisSettings())
    val connection by ServiceLocator.connection.collectAsState()
    val session by discovery.session.collectAsState()
    val topics by discovery.topics.collectAsState()

    var filter by remember(settings.topicPrefix) { mutableStateOf("${settings.topicPrefix}/#") }
    var seconds by remember { mutableStateOf(TopicDiscovery.DEFAULT_SECONDS.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf<DiscoveredTopic?>(null) }
    var imported by remember { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Discover topics", style = MaterialTheme.typography.titleLarge)
        Text(
            "Listens on the filter for a limited time and lists the topics it sees. " +
                "Payloads are shown only as short, redacted previews and are never acted on.",
            style = MaterialTheme.typography.bodySmall,
        )
        Field("Topic filter", filter, enabled = session == null, mono = true) { filter = it }
        Field(
            "Seconds (max ${TopicDiscovery.MAX_SECONDS})", seconds, KeyboardType.Number,
            enabled = session == null, mono = true,
        ) { seconds = it.filter(Char::isDigit).take(3) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        imported?.let { Text(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (session == null) {
                Button(
                    enabled = connection.state == ConnState.CONNECTED,
                    onClick = { error = discovery.start(filter.trim(), seconds.toIntOrNull() ?: 0) },
                ) { Text("Start") }
            } else {
                Button(onClick = { discovery.stop() }) { Text("Stop") }
            }
            OutlinedButton(onClick = { discovery.stop(); onClose() }) { Text("Close") }
        }
        if (connection.state != ConnState.CONNECTED) Text("Not connected to a broker.")
        Text(if (session != null) "Listening… ${topics.size} topics" else "${topics.size} topics seen")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(topics, key = { it.topic }) { t ->
                ConsoleCard {
                    MonoText(t.topic, style = MonoStyle.copy(fontWeight = FontWeight.SemiBold))
                    MonoText(
                        "×${t.count} · ${t.lastSize} B${if (t.retained) " · retained" else ""} · ${t.preview}",
                        style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = 4,
                    )
                    TextButton(onClick = { importing = t }) { Text("Import as custom topic") }
                }
            }
        }
    }

    importing?.let { t ->
        ImportDialog(t, settings, onDone = { msg -> importing = null; imported = msg })
    }
}

@Composable
private fun ImportDialog(t: DiscoveredTopic, settings: DradisSettings, onDone: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(t.topic.substringAfterLast('/').take(64).ifBlank { "topic" }) }
    var type by remember { mutableStateOf(t.suggested) }
    var extractor by remember { mutableStateOf("") }
    var notify by remember { mutableStateOf(false) }
    var speak by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text("Import as custom topic") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MonoText(t.topic, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                Field("Name", name) { name = it.take(64) }
                Text("Type")
                CustomTopicType.entries.forEach { opt ->
                    FilterChip(selected = type == opt, onClick = { type = opt }, label = { Text(opt.name) })
                }
                if (type == CustomTopicType.EXTRACTED) {
                    Field("JSONPath (\$.a.b) or regex", extractor, mono = true) { extractor = it.take(200) }
                }
                ToggleRow("Notify", checked = notify) { notify = it }
                ToggleRow("Speak", checked = speak) { speak = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val repo = ServiceLocator.customTopics
                    val builtIn = Topics(settings.topicPrefix, settings.deviceName).let { it.inboundTopics() + it.outboundTopics() }
                    val topic = CustomTopic(
                        id = UUID.randomUUID().toString(), name = name.trim(), filter = t.topic, type = type,
                        notify = notify, speak = speak, extractor = extractor.trim(),
                    )
                    try {
                        val err = repo.save(repo.topics.first() + topic, builtIn)
                        if (err == null) onDone("Imported \"${topic.name}\"") else error = err
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = "Could not save: ${e.message ?: e.javaClass.simpleName}"
                    }
                }
            }) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } },
    )
}
