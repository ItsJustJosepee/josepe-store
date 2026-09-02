package store.josepe.dev.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalBlurEnabled = staticCompositionLocalOf { true }

@Composable
expect fun isPlatformSystemDarkTheme(): Boolean

@Composable
expect fun PlatformThemeHook(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    colorScheme: ColorScheme
): ColorScheme
