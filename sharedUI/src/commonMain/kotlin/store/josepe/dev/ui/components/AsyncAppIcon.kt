package store.josepe.dev.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

object ImageCache {
    private val cache = mutableMapOf<String, ImageBitmap>()
    private val client = HttpClient()

    suspend fun load(url: String): ImageBitmap? = withContext(Dispatchers.Default) {
        if (cache.containsKey(url)) return@withContext cache[url]
        try {
            val responseBytes: ByteArray = client.get(url).body()
            val bitmap = responseBytes.decodeToImageBitmap()
            cache[url] = bitmap
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
fun AsyncAppIcon(
    iconUrl: String?,
    displayName: String,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp)
) {
    var bitmap by remember(iconUrl) { mutableStateOf<ImageBitmap?>(null) }
    var hasFailed by remember(iconUrl) { mutableStateOf(false) }

    LaunchedEffect(iconUrl) {
        if (!iconUrl.isNullOrEmpty()) {
            val loaded = ImageCache.load(iconUrl)
            if (loaded != null) {
                bitmap = loaded
            } else {
                hasFailed = true
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = bitmap) { currentBitmap ->
            if (currentBitmap != null) {
                Image(
                    bitmap = currentBitmap,
                    contentDescription = displayName,
                    modifier = Modifier.size(size),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Fallback gradient box with stylized typography
                val gradient = Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                )
                Box(
                    modifier = Modifier
                        .size(size)
                        .background(gradient),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayName.take(2).uppercase(),
                        fontWeight = FontWeight.Black,
                        fontSize = (size.value * 0.38f).sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}
