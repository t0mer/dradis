package dev.tomerklein.dradis.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG 2.x contrast ratio between two opaque colours. */
fun contrast(a: Color, b: Color): Double {
    fun lin(c: Float): Double = c.toDouble().let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
    fun lum(c: Color) = 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    val (hi, lo) = lum(a).let { x -> lum(b).let { y -> maxOf(x, y) to minOf(x, y) } }
    return (hi + 0.05) / (lo + 0.05)
}

class PaletteContrastTest {
    private val palettes = mapOf("dark" to ConsolePalette.dark, "light" to ConsolePalette.light)

    @Test fun textPairsMeetAA() {
        for ((name, p) in palettes) {
            val pairs = mapOf(
                "text/background" to (p.text to p.background),
                "text/surface" to (p.text to p.surface),
                "textSecondary/background" to (p.textSecondary to p.background),
                "textSecondary/surface" to (p.textSecondary to p.surface),
                "onPrimary/primary" to (p.onPrimary to p.primary),
                "primary/surface" to (p.primary to p.surface),
                "onPrimaryContainer/primaryContainer" to (p.onPrimaryContainer to p.primaryContainer),
                "error/surface" to (p.error to p.surface),
                "onErrorContainer/errorContainer" to (p.onErrorContainer to p.errorContainer),
                "onSuccessContainer/successContainer" to (p.onSuccessContainer to p.successContainer),
                "warning/surface" to (p.warning to p.surface),
                "onWarningContainer/warningContainer" to (p.onWarningContainer to p.warningContainer),
                "logIn/surface" to (p.logIn to p.surface),
                "logOut/surface" to (p.logOut to p.surface),
            )
            for ((label, pair) in pairs) {
                val r = contrast(pair.first, pair.second)
                assertTrue("$name $label = %.2f, needs >= 4.5".format(r), r >= 4.5)
            }
        }
    }

    @Test fun nonTextMeetsThree() {
        for ((name, p) in palettes) {
            val pairs = mapOf(
                "outline/surface" to (p.outline to p.surface),
                "outline/background" to (p.outline to p.background),
                "primary/surface" to (p.primary to p.surface),
            )
            for ((label, pair) in pairs) {
                val r = contrast(pair.first, pair.second)
                assertTrue("$name $label = %.2f, needs >= 3.0".format(r), r >= 3.0)
            }
        }
    }
}
