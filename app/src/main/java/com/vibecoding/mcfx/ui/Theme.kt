package com.vibecoding.mcfx.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Palette and motion taken from Wise's own design tokens (read off
 * wise.com/zh-cn/currency-converter/jpy-to-cny-rate):
 *
 *   --coin-colour                     #9fe870   primary action (lime)
 *   hero background                   #163300   forest
 *   --color-background-accent         #ebf9ff   info banner
 *   --color-content-primary           #37517e   info banner text
 *   --color-border-neutral            rgba(0,0,0,.1)
 *   --radius-small / medium / full    10px / 16px / 9999px
 *   font                              Inter, Helvetica, Arial
 */
val WiseGreen = Color(0xFF9FE870)
val WiseGreenHover = Color(0xFF80E142)
val WiseForest = Color(0xFF163300)
val WiseForestDeep = Color(0xFF0E2400)
val WiseOnGreen = Color(0xFF163300)

val WiseInk = Color(0xFF0E0F0C)
val WiseInkSoft = Color(0xFF454745)
val WiseInkFaint = Color(0xFF6A6C6A)
val WiseBorder = Color(0x1A000000)
val WiseFill = Color(0xFFF2F5F6)

val WiseInfoBg = Color(0xFFEBF9FF)
val WiseInfoInk = Color(0xFF37517E)

val WiseNegative = Color(0xFFCF2929)
val WiseNegativeBg = Color(0x14CF2929)

/** Mastercard brand mark colours, used by the loading mark and the launcher icon. */
val BrandRed = Color(0xFFEB001B)
val BrandOrange = Color(0xFFF79E1B)
val BrandOverlap = Color(0xFFFF5F00)

/** Radius scale (px tokens from Wise, mapped to dp at call sites). */
object WiseRadius {
    val small = 10
    val medium = 16
    val xLarge = 32
}

/** Wise's motion feel: quick, eased, no bounce. */
object WiseMotion {
    const val fast = 180
    const val medium = 320
    val easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

private val LightColors = lightColorScheme(
    primary = WiseForest,
    onPrimary = WiseGreen,
    primaryContainer = WiseGreen,
    onPrimaryContainer = WiseForest,
    secondary = WiseGreen,
    onSecondary = WiseForest,
    background = Color.White,
    onBackground = WiseInk,
    surface = Color.White,
    onSurface = WiseInk,
    surfaceVariant = WiseFill,
    onSurfaceVariant = WiseInkSoft,
    outline = WiseBorder,
    outlineVariant = WiseBorder,
    error = WiseNegative,
    onError = Color.White,
)

@Composable
fun McfxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
