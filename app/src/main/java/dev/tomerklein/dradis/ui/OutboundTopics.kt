package dev.tomerklein.dradis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tomerklein.dradis.settings.OutboundSource
import dev.tomerklein.dradis.settings.OutboundTopic
import dev.tomerklein.dradis.ui.components.ConsoleCard
import dev.tomerklein.dradis.ui.components.Field
import dev.tomerklein.dradis.ui.components.MonoText
import dev.tomerklein.dradis.ui.components.ToggleRow
import java.util.UUID
import kotlinx.coroutines.launch

internal fun OutboundSource.label(): String = when (this) {
    OutboundSource.BATTERY_LEVEL -> "Battery level"
    OutboundSource.CHARGING -> "Charging"
    OutboundSource.WIFI_SSID -> "Wi-Fi SSID"
    OutboundSource.LOCATION -> "Location"
}

@Composable
internal fun OutboundCard(t: OutboundTopic, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    ConsoleCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                MonoText(t.topic)
                Text(
                    "${t.source.label()}${if (t.retain) " · retained" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(t.enabled, onToggle)
        }
        Row {
            TextButton(onClick = onEdit) { Text("Edit") }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

/** Add (initial == null) or edit an outbound topic. [onSave] returns an error to show, or null when saved. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OutboundEditorDialog(
    initial: OutboundTopic?,
    onDismiss: () -> Unit,
    onSave: suspend (OutboundTopic) -> String?,
) {
    val scope = rememberCoroutineScope()
    var topic by remember { mutableStateOf(initial?.topic ?: "") }
    var source by remember { mutableStateOf(initial?.source ?: OutboundSource.BATTERY_LEVEL) }
    var retain by remember { mutableStateOf(initial?.retain ?: false) }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add outbound topic" else "Edit outbound topic") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Field("Topic to publish to", topic, mono = true) { topic = it.take(256) }
                Text("Value")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutboundSource.entries.forEach { opt ->
                        FilterChip(selected = source == opt, onClick = { source = opt }, label = { Text(opt.label()) })
                    }
                }
                ToggleRow("Retained", "the broker keeps the last value", checked = retain) { retain = it }
                ToggleRow("Enabled", checked = enabled) { enabled = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                saving = true
                scope.launch {
                    error = onSave(
                        OutboundTopic(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            topic = topic.trim(), source = source, retain = retain, enabled = enabled,
                        ),
                    )
                    saving = false
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
