package store.josepe.dev.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min

fun Color.toHsl(): FloatArray {
    val r = red
    val g = green
    val b = blue

    val maxVal = max(r, max(g, b))
    val minVal = min(r, min(g, b))
    val delta = maxVal - minVal

    var h = 0f
    val l = (maxVal + minVal) / 2f
    var s = 0f

    if (delta != 0f) {
        s = if (l <= 0.5f) delta / (maxVal + minVal) else delta / (2f - maxVal - minVal)

        h = when (maxVal) {
            r -> ((g - b) / delta) + (if (g < b) 6f else 0f)
            g -> ((b - r) / delta) + 2f
            else -> ((r - g) / delta) + 4f
        }
        h *= 60f
    }

    return floatArrayOf(h, s, l)
}

fun fromHsl(hue: Float, saturation: Float, lightness: Float): Color {
    val h = (hue % 360f + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)

    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f

    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }

    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = 1f
    )
}

fun Color.lighten(factor: Float): Color {
    val hsl = toHsl()
    return fromHsl(hsl[0], hsl[1], (hsl[2] + (1f - hsl[2]) * factor).coerceIn(0f, 1f))
}

fun Color.darken(factor: Float): Color {
    val hsl = toHsl()
    return fromHsl(hsl[0], hsl[1], (hsl[2] * (1f - factor)).coerceIn(0f, 1f))
}

fun Color.contrastingTextColor(): Color {
    val luminance = 0.299f * red + 0.587f * green + 0.114f * blue
    return if (luminance > 0.5f) Color(0xFF0F172A) else Color.White
}

fun parseHexColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val clean = hex.removePrefix("#").trim()
    return runCatching {
        val longVal = clean.toLong(16)
        if (clean.length == 6) {
            Color(longVal or 0xFF000000)
        } else {
            Color(longVal)
        }
    }.getOrNull()
}

fun getCustomColors(primary: Color, secondary: Color, isDark: Boolean): ColorScheme {
    val hsl = primary.toHsl()
    val hue = hsl[0]

    return if (isDark) {
        val bgColor = fromHsl(hue, 0.12f, 0.07f)
        val surfaceColor = fromHsl(hue, 0.12f, 0.11f)
        val surfaceVariantColor = fromHsl(hue, 0.14f, 0.17f)
        val onBg = fromHsl(hue, 0.05f, 0.94f)
        val onSurface = fromHsl(hue, 0.05f, 0.94f)
        val onSurfaceVariant = fromHsl(hue, 0.10f, 0.75f)
        val outlineColor = fromHsl(hue, 0.12f, 0.32f)

        darkColorScheme(
            primary = primary,
            onPrimary = primary.contrastingTextColor(),
            primaryContainer = fromHsl(hue, 0.40f, 0.22f),
            onPrimaryContainer = fromHsl(hue, 0.20f, 0.90f),
            secondary = secondary,
            onSecondary = secondary.contrastingTextColor(),
            secondaryContainer = fromHsl(hue, 0.30f, 0.25f),
            onSecondaryContainer = fromHsl(hue, 0.15f, 0.90f),
            background = bgColor,
            onBackground = onBg,
            surface = surfaceColor,
            onSurface = onSurface,
            surfaceVariant = surfaceVariantColor,
            onSurfaceVariant = onSurfaceVariant,
            outline = outlineColor,
            error = Color(0xFFEF4444),
            onError = Color.White
        )
    } else {
        val bgColor = fromHsl(hue, 0.08f, 0.98f)
        val surfaceColor = fromHsl(hue, 0.08f, 0.96f)
        val surfaceVariantColor = fromHsl(hue, 0.12f, 0.91f)
        val onBg = fromHsl(hue, 0.10f, 0.08f)
        val onSurface = fromHsl(hue, 0.10f, 0.08f)
        val onSurfaceVariant = fromHsl(hue, 0.14f, 0.35f)
        val outlineColor = fromHsl(hue, 0.14f, 0.72f)

        lightColorScheme(
            primary = primary,
            onPrimary = primary.contrastingTextColor(),
            primaryContainer = fromHsl(hue, 0.45f, 0.90f),
            onPrimaryContainer = fromHsl(hue, 0.45f, 0.18f),
            secondary = secondary,
            onSecondary = secondary.contrastingTextColor(),
            secondaryContainer = fromHsl(hue, 0.30f, 0.92f),
            onSecondaryContainer = fromHsl(hue, 0.30f, 0.22f),
            background = bgColor,
            onBackground = onBg,
            surface = surfaceColor,
            onSurface = onSurface,
            surfaceVariant = surfaceVariantColor,
            onSurfaceVariant = onSurfaceVariant,
            outline = outlineColor,
            error = Color(0xFFDC2626),
            onError = Color.White
        )
    }
}
