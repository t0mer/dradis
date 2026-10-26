package dev.tomerklein.dradis.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tomerklein.dradis.ServiceLocator
import dev.tomerklein.dradis.commands.ReceivedValue
import dev.tomerklein.dradis.mqtt.Topics
import dev.tomerklein.dradis.settings.CustomTopic
import dev.tomerklein.dradis.settings.CustomTopicType
import dev.tomerklein.dradis.settings.DradisSettings
import dev.tomerklein.dradis.settings.NotifyIcon
import dev.tomerklein.dradis.settings.TopicsTransfer
import dev.tomerklein.dradis.ui.MonoStyle
import dev.tomerklein.dradis.ui.components.ConsoleCard
import dev.tomerklein.dradis.ui.components.Field
import dev.tomerklein.dradis.ui.components.MonoText
import dev.tomerklein.dradis.ui.components.ToggleRow
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Custom inbound topics: list with enable switch and last value, add/edit/delete, JSON import/export. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopicsScreen(modifier: Modifier = Modifier) {
    val repo = ServiceLocator.customTopics
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val topics by repo.topics.collectAsState(initial = emptyList())
    val values by ServiceLocator.customTopicValues.collectAsState()
    val settings by ServiceLocator.settings.settings.collectAsState(initial = DradisSettings())
    val builtIn = remember(settings.topicPrefix, settings.deviceName) {
        Topics(settings.topicPrefix, settings.deviceName).let { it.inboundTopics() + it.outboundTopics() }
    }

    var message by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<CustomTopic?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CustomTopic?>(null) }

    /** Atomically applies [change] to the stored list; returns the error to show, or null. */
    suspend fun update(change: (List<CustomTopic>) -> List<CustomTopic>): String? = try {
        repo.update(builtIn, change)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        "Could not save: ${e.message ?: e.javaClass.simpleName}"
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val text = TopicsTransfer.export(repo.topics.first())
            message = withContext(Dispatchers.IO) {
                runCatching {
                    (context.contentResolver.openOutputStream(uri) ?: error("no stream")).use { it.write(text.toByteArray()) }
                }
                    .fold({ "Exported" }, { "Export failed" })
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            message = try {
                val text = withContext(Dispatchers.IO) { readBounded(context, uri) }
                var result: TopicsTransfer.Result? = null
                val err = withContext(Dispatchers.Default) {
                    update { cur ->
                        when (val r = TopicsTransfer.import(text, cur, builtIn)) {
                            is TopicsTransfer.Result.Ok -> r.merged.also { result = r }
                            is TopicsTransfer.Result.Error -> cur.also { result = r }
                        }
                    }
                }
                when (val r = result) {
                    is TopicsTransfer.Result.Error -> r.message
                    is TopicsTransfer.Result.Ok -> err ?: "Imported ${r.added} topics (disabled until you switch them on)"
                    null -> err
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message ?: "Could not read the file"
            }
        }
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { adding = true }) { Text("Add") }
            OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Import") }
            OutlinedButton(enabled = topics.isNotEmpty(), onClick = { exporter.launch("dradis-topics.json") }) { Text("Export") }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (topics.isEmpty()) {
            Text("No custom topics yet. Add one, import a file, or use Discover (search icon) to pick from live traffic.")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(topics, key = { it.id }) { t ->
                TopicCard(
                    t, values[t.id],
                    onToggle = { on -> scope.launch { message = update { l -> l.map { if (it.id == t.id) it.copy(enabled = on) else it } } } },
                    onEdit = { editing = t },
                    onDelete = { deleting = t },
                )
            }
        }
    }

    if (adding) {
        TopicEditorDialog(
            initial = null,
            onDismiss = { adding = false },
            onSave = { t -> update { it + t }?.also { return@TopicEditorDialog it }; adding = false; null },
        )
    }
    editing?.let { cur ->
        TopicEditorDialog(
            initial = cur,
            onDismiss = { editing = null },
            onSave = { t -> update { l -> l.map { if (it.id == cur.id) t else it } }?.also { return@TopicEditorDialog it }; editing = null; null },
        )
    }
    deleting?.let { t ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${t.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { message = update { l -> l.filter { it.id != t.id } } }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TopicCard(
    t: CustomTopic,
    last: ReceivedValue?,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ConsoleCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.name, style = MaterialTheme.typography.titleSmall)
                MonoText("${t.type.name} · ${t.filter}", style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            }
            Switch(t.enabled, onToggle)
        }
        last?.let { MonoText("Last: ${it.label?.let { l -> "$l: " } ?: ""}${it.value}") }
        Row {
            TextButton(onClick = onEdit) { Text("Edit") }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

/** Add (initial == null) or edit a topic. [onSave] returns an error to show, or null when saved. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicEditorDialog(
    initial: CustomTopic?,
    onDismiss: () -> Unit,
    onSave: suspend (CustomTopic) -> String?,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var filter by remember { mutableStateOf(initial?.filter ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: CustomTopicType.TEXT) }
    var extractor by remember { mutableStateOf(initial?.extractor ?: "") }
    var separator by remember { mutableStateOf(initial?.separator ?: ":") }
    var notify by remember { mutableStateOf(initial?.notify ?: false) }
    var speak by remember { mutableStateOf(initial?.speak ?: false) }
    var icon by remember { mutableStateOf(initial?.icon ?: NotifyIcon.DEFAULT) }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add custom topic" else "Edit custom topic") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Field("Name", name) { name = it.take(64) }
                Field("Topic filter (+ and # allowed)", filter, mono = true) { filter = it.take(256) }
                Text("Type")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CustomTopicType.entries.forEach { opt ->
                        FilterChip(selected = type == opt, onClick = { type = opt }, label = { Text(opt.name) })
                    }
                }
                if (type == CustomTopicType.EXTRACTED) {
                    Field("JSONPath (\$.a.b) or regex", extractor, mono = true) { extractor = it.take(200) }
                }
                if (type == CustomTopicType.KEY_VALUE) {
                    Field("Separator", separator, mono = true) { separator = it.take(8) }
                }
                ToggleRow("Enabled", checked = enabled) { enabled = it }
                ToggleRow("Notify", checked = notify) { notify = it }
                ToggleRow("Speak", checked = speak) { speak = it }
                if (notify) {
                    Text("Notification icon")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NotifyIcon.entries.forEach { opt ->
                            FilterChip(selected = icon == opt, onClick = { icon = opt }, label = { Text(opt.name) })
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                saving = true
                scope.launch {
                    error = onSave(
                        CustomTopic(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(), filter = filter.trim(), type = type, enabled = enabled,
                            notify = notify, speak = speak, icon = icon,
                            extractor = extractor.trim(), separator = separator,
                        ),
                    )
                    saving = false
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Reads at most [TopicsTransfer.MAX_FILE_BYTES]; fails instead of buffering a huge file. */
private fun readBounded(context: Context, uri: Uri): String {
    val input = context.contentResolver.openInputStream(uri) ?: error("Could not read the file")
    input.use {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = it.read(buf)
            if (n < 0) break
            total += n
            if (total > TopicsTransfer.MAX_FILE_BYTES) error("File is too large")
            out.write(buf, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
