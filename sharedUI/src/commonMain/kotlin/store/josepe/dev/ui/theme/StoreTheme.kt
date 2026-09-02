package store.josepe.dev.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8B5CF6), // Purple accent (aligned with Josepe Dev)
    onPrimary = Color.White,
    primaryContainer = Color(0xFF6D28D9),
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = Color(0xFF06B6D4), // Cyan accent
    onSecondary = Color.Black,
    background = Color(0xFF0F172A), // Dark slate
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF475569)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF7C3AED),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF4C1D95),
    secondary = Color(0xFF0891B2),
    onSecondary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1)
)

val StoreCardShape = RoundedCornerShape(16.dp)
val StoreButtonShape = RoundedCornerShape(12.dp)

@Composable
fun JosepeStoreTheme(
    darkTheme: Boolean = isPlatformSystemDarkTheme(),
    dynamicColor: Boolean = true,
    blurEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val baseScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val finalColorScheme = PlatformThemeHook(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        colorScheme = baseScheme
    )

    MaterialTheme(
        colorScheme = finalColorScheme,
        shapes = Shapes(
            small = RoundedCornerShape(8.dp),
            medium = StoreCardShape,
            large = RoundedCornerShape(20.dp)
        ),
        content = {
            CompositionLocalProvider(LocalBlurEnabled provides blurEnabled) {
                content()
            }
        }
    )
}
