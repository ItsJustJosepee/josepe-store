package store.josepe.dev.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

@Composable
fun StoreMarkdown(
    content: String,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val currentContentColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary

    val colors = markdownColor(
        text = currentContentColor,
        codeText = MaterialTheme.colorScheme.onSurfaceVariant,
        inlineCodeText = primaryColor,
        linkText = primaryColor
    )

    val typography = if (isCompact) {
        // Tuned for Android phones and small cards
        markdownTypography(
            h1 = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
            h2 = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
            h3 = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            h4 = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            h5 = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
            h6 = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            text = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp)
        )
    } else {
        // Tuned for Tablets, Desktop, and full dialog view
        markdownTypography(
            h1 = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            h2 = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
            h3 = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            h4 = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            h5 = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
            h6 = MaterialTheme.typography.labelSmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
            text = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 20.sp)
        )
    }

    Markdown(
        content = content,
        colors = colors,
        typography = typography,
        modifier = modifier
    )
}
