package dev.tomerklein.dradis.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun PaletteColors.darkScheme(): ColorScheme = darkColorScheme(
    primary = primary, onPrimary = onPrimary, primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
    secondary = primary, onSecondary = onPrimary, secondaryContainer = primaryContainer, onSecondaryContainer = onPrimaryContainer,
    background = background, onBackground = text, surface = surface, onSurface = text,
    surfaceVariant = surface, onSurfaceVariant = textSecondary, surfaceTint = primary,
    surfaceContainer = surface, surfaceContainerLow = background, surfaceContainerHigh = surface,
    surfaceContainerHighest = surface, surfaceContainerLowest = background,
    outline = outline, outlineVariant = outline,
    error = error, onError = onError, errorContainer = errorContainer, onErrorContainer = onErrorContainer,
)

private fun PaletteColors.lightScheme(): ColorScheme = lightColorScheme(
    primary = primary, onPrimary = onPrimary, primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
    secondary = primary, onSecondary = onPrimary, secondaryContainer = primaryContainer, onSecondaryContainer = onPrimaryContainer,
    background = background, onBackground = text, surface = surface, onSurface = text,
    surfaceVariant = surface, onSurfaceVariant = textSecondary, surfaceTint = primary,
    surfaceContainer = surface, surfaceContainerLow = background, surfaceContainerHigh = surface,
    surfaceContainerHighest = surface, surfaceContainerLowest = background,
    outline = outline, outlineVariant = outline,
    error = error, onError = onError, errorContainer = errorContainer, onErrorContainer = onErrorContainer,
)

private val ConsoleShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

private val base = Typography()
private val ConsoleTypography = base.copy(
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
)

/** App-wide Material 3 theme: the fixed Console palette, following the system light/dark setting.
 *  Dynamic (wallpaper) colour is intentionally not used. */
@Composable
fun DradisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) ConsolePalette.dark else ConsolePalette.light
    val scheme = if (darkTheme) palette.darkScheme() else palette.lightScheme()
    CompositionLocalProvider(LocalExtendedColors provides palette.toExtended()) {
        MaterialTheme(colorScheme = scheme, shapes = ConsoleShapes, typography = ConsoleTypography, content = content)
    }
}
