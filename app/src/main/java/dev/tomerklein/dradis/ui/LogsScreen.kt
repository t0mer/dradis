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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tomerklein.dradis.ServiceLocator
import dev.tomerklein.dradis.log.Direction
import dev.tomerklein.dradis.log.LogEntry
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
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry, time: String) {
    val (tag, color) = when (entry.direction) {
        Direction.IN -> "IN " to Color(0xFF2E7D32)
        Direction.OUT -> "OUT" to Color(0xFF1565C0)
        Direction.INFO -> "··" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row {
            Text(time, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("  $tag  ", fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, color = color)
            if (entry.topic.isNotBlank()) {
                Text(entry.topic, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold)
            }
        }
        if (entry.payload.isNotBlank()) {
            Text(
                entry.payload.take(500),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun Row(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) = androidx.compose.foundation.layout.Row(modifier, horizontalArrangement, content = content)
