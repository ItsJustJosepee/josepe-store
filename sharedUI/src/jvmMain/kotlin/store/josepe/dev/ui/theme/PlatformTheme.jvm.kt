package store.josepe.dev.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import store.josepe.dev.ui.util.DesktopSystemThemeHelper

@Composable
actual fun isPlatformSystemDarkTheme(): Boolean = DesktopSystemThemeHelper.isSystemDarkTheme()

@Composable
actual fun PlatformThemeHook(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    colorScheme: ColorScheme
): ColorScheme {
    if (dynamicColor) {
        val accentHex = DesktopSystemThemeHelper.getSystemAccentColorHex()
        if (accentHex != null) {
            val parsedColor = parseHexColor(accentHex)
            if (parsedColor != null) {
                val secondary = parsedColor.lighten(0.2f)
                return getCustomColors(parsedColor, secondary, darkTheme)
            }
        }
    }
    return colorScheme
}
