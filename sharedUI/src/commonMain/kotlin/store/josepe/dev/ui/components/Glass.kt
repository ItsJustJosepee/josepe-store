package store.josepe.dev.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import store.josepe.dev.ui.theme.LocalBlurEnabled

/**
 * Glassmorphism centralizado para Josepe Store con Haze.
 */
@Composable
fun Modifier.glassEffect(
    hazeState: HazeState?,
    tintColor: Color = MaterialTheme.colorScheme.surface,
    tintAlpha: Float = 0.70f,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    blurRadius: Dp = 20.dp,
    noiseFactor: Float = 0.015f,
): Modifier {
    val blurEnabled = LocalBlurEnabled.current
    return if (blurEnabled && hazeState != null) {
        this.hazeEffect(
            state = hazeState,
            style = HazeStyle(
                blurRadius = blurRadius,
                tint = HazeTint(tintColor.copy(alpha = tintAlpha)),
                noiseFactor = noiseFactor,
                backgroundColor = backgroundColor,
            )
        )
    } else {
        this
    }
}

/**
 * Color de contenedor que acompaña a [glassEffect].
 */
@Composable
fun glassContainerColor(
    hazeState: HazeState?,
    opaqueColor: Color = MaterialTheme.colorScheme.surface,
    fallbackAlpha: Float = 0.90f,
): Color {
    val blurEnabled = LocalBlurEnabled.current
    return if (blurEnabled && hazeState != null) {
        Color.Transparent
    } else {
        opaqueColor.copy(alpha = if (blurEnabled) fallbackAlpha else 1f)
    }
}
