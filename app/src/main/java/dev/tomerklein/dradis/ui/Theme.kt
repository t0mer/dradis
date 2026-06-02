package dev.tomerklein.dradis.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DradisGreen = Color(0xFF1F8F63)
private val DradisGreenLight = Color(0xFF36F1A0)

private val DarkColors = darkColorScheme(
    primary = DradisGreenLight,
    secondary = DradisGreen,
)
private val LightColors = lightColorScheme(
    primary = DradisGreen,
    secondary = DradisGreenLight,
)

/** App-wide Material 3 theme. Honours the system light/dark preference and
 *  uses dynamic colour on Android 12+ when available. */
@Composable
fun DradisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
