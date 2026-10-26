package dev.tomerklein.dradis.ui

import androidx.compose.ui.graphics.Color

/** Every colour of the Console look for one theme. Pairs are checked by PaletteContrastTest. */
data class PaletteColors(
    val background: Color,
    val surface: Color,
    val outline: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val text: Color,
    val textSecondary: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val logIn: Color,
    val logOut: Color,
)

/** Fixed brand palette (no dynamic colour): near-black green with a mint accent, and its light inversion. */
object ConsolePalette {
    val dark = PaletteColors(
        background = Color(0xFF0E1512),
        surface = Color(0xFF16211C),
        outline = Color(0xFF55796A),
        primary = Color(0xFF3DDC97),
        onPrimary = Color(0xFF00331F),
        primaryContainer = Color(0xFF103A2C),
        onPrimaryContainer = Color(0xFFB8F5D8),
        text = Color(0xFFD8E6DF),
        textSecondary = Color(0xFF9DB3A8),
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF3B0000),
        errorContainer = Color(0xFF4A1D1D),
        onErrorContainer = Color(0xFFFFD6D6),
        success = Color(0xFF3DDC97),
        successContainer = Color(0xFF103A2C),
        onSuccessContainer = Color(0xFFB8F5D8),
        warning = Color(0xFFFFB74D),
        warningContainer = Color(0xFF4A3410),
        onWarningContainer = Color(0xFFFFE0B2),
        logIn = Color(0xFF3DDC97),
        logOut = Color(0xFF6CB6FF),
    )

    val light = PaletteColors(
        background = Color(0xFFF3F8F5),
        surface = Color(0xFFFFFFFF),
        outline = Color(0xFF6F8E80),
        primary = Color(0xFF0F7A52),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDDF3E8),
        onPrimaryContainer = Color(0xFF073B28),
        text = Color(0xFF12211A),
        textSecondary = Color(0xFF4A5F55),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFBE3E0),
        onErrorContainer = Color(0xFF5C0F0A),
        success = Color(0xFF0F7A52),
        successContainer = Color(0xFFDDF3E8),
        onSuccessContainer = Color(0xFF073B28),
        warning = Color(0xFF8A5300),
        warningContainer = Color(0xFFFFF0D6),
        onWarningContainer = Color(0xFF4A2C00),
        logIn = Color(0xFF0F7A52),
        logOut = Color(0xFF1565C0),
    )
}
