package dev.tomerklein.dradis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tomerklein.dradis.ServiceLocator
import dev.tomerklein.dradis.log.Direction
import dev.tomerklein.dradis.log.LogEntry
import dev.tomerklein.dradis.ui.MonoStyle
import dev.tomerklein.dradis.ui.components.MonoText
import dev.tomerklein.dradis.ui.extended
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(modifier: Modifier = Modifier) {
    val entries by ServiceLocator.mqttLog.entries.collectAsState()
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { ServiceLocator.mqttLog.clear() }) { Text("Clear") }
        }
        if (entries.isEmpty()) {
            Text(
                "No MQTT activity yet.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true,
            ) {
                items(entries.asReversed()) { entry ->
                    LogRow(entry, timeFmt.format(Date(entry.timeMillis)))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry, time: String) {
    val (tag, color) = when (entry.direction) {
        Direction.IN -> "IN " to MaterialTheme.extended.logIn
        Direction.OUT -> "OUT" to MaterialTheme.extended.logOut
        Direction.INFO -> "··" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row {
            MonoText(time, style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            MonoText("  $tag  ", style = MonoStyle.copy(fontWeight = FontWeight.Bold, color = color))
            if (entry.topic.isNotBlank()) {
                MonoText(entry.topic, style = MonoStyle.copy(fontWeight = FontWeight.SemiBold))
            }
        }
        if (entry.payload.isNotBlank()) {
            MonoText(entry.payload.take(500))
        }
    }
}

@Composable
private fun Row(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) = androidx.compose.foundation.layout.Row(modifier, horizontalArrangement, content = content)
