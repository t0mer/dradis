package dev.tomerklein.dradis.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/** Colours Material 3's ColorScheme has no role for: success/warning states and log directions. */
data class ExtendedColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val logIn: Color,
    val logOut: Color,
)

internal fun PaletteColors.toExtended() = ExtendedColors(
    success = success, successContainer = successContainer, onSuccessContainer = onSuccessContainer,
    warning = warning, warningContainer = warningContainer, onWarningContainer = onWarningContainer,
    logIn = logIn, logOut = logOut,
)

val LocalExtendedColors = staticCompositionLocalOf<ExtendedColors> { error("ExtendedColors used outside DradisTheme") }

val MaterialTheme.extended: ExtendedColors
    @Composable @ReadOnlyComposable get() = LocalExtendedColors.current

/** Monospace style for technical data: topics, filters, hosts, payloads, log lines. */
val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp)
