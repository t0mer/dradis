package dev.tomerklein.dradis.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tomerklein.dradis.mqtt.ConnState
import dev.tomerklein.dradis.ui.MonoStyle
import dev.tomerklein.dradis.ui.extended

/** Visual class of a status chip; the state name is always shown as text too, never colour alone. */
enum class ChipState { SUCCESS, WARNING, ERROR }

fun chipStateFor(state: ConnState): ChipState = when (state) {
    ConnState.CONNECTED -> ChipState.SUCCESS
    ConnState.CONNECTING -> ChipState.WARNING
    ConnState.DISCONNECTED, ConnState.UNAUTHORIZED -> ChipState.ERROR
}

@Composable
fun StateChip(label: String, state: ChipState, modifier: Modifier = Modifier) {
    val ext = MaterialTheme.extended
    val scheme = MaterialTheme.colorScheme
    val (fill, content) = when (state) {
        ChipState.SUCCESS -> ext.successContainer to ext.onSuccessContainer
        ChipState.WARNING -> ext.warningContainer to ext.onWarningContainer
        ChipState.ERROR -> scheme.errorContainer to scheme.onErrorContainer
    }
    val dot = when (state) {
        ChipState.SUCCESS -> ext.success
        ChipState.WARNING -> ext.warning
        ChipState.ERROR -> scheme.error
    }
    Surface(
        color = fill, contentColor = content, shape = CircleShape, modifier = modifier,
        border = BorderStroke(1.dp, dot.copy(alpha = 0.6f)),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(dot, CircleShape))
            Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Outlined, flat card; [hero] uses the primary container for the key card on a screen, [error] the error container. */
@Composable
fun ConsoleCard(
    modifier: Modifier = Modifier,
    hero: Boolean = false,
    error: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val container = when { error -> scheme.errorContainer; hero -> scheme.primaryContainer; else -> scheme.surface }
    val onContainer = when { error -> scheme.onErrorContainer; hero -> scheme.onPrimaryContainer; else -> scheme.onSurface }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
        border = BorderStroke(1.dp, if (error) scheme.error else if (hero) scheme.primary else scheme.outline),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Technical data: monospace, wraps instead of overflowing. */
@Composable
fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MonoStyle,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(text, modifier, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
fun KeyValueRow(label: String, value: String, mono: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        // The label may wrap but never takes more than ~40% of the row, so the value keeps room.
        Text(
            label,
            Modifier.fillMaxWidth(0.4f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val end = TextAlign.End
        if (mono) MonoText(value, Modifier.weight(1f), style = MonoStyle.copy(textAlign = end), maxLines = Int.MAX_VALUE)
        else Text(value, Modifier.weight(1f), textAlign = end)
    }
}

@Composable
fun ToggleRow(
    label: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    enabled: Boolean = true,
    mono: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        textStyle = if (mono && !password) MonoStyle else MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else keyboardType,
            autoCorrectEnabled = if (password) false else null,
        ),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
    )
}
