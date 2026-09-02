package store.josepe.dev.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class VersionSection(
    val version: String,
    val date: String?,
    val subtitle: String?,
    val fullTitle: String,
    val content: String,
    val isLatest: Boolean
)

data class ParsedChangelog(
    val introTitle: String,
    val introDescription: String,
    val sections: List<VersionSection>
)

fun parseChangelog(rawText: String): ParsedChangelog {
    val sections = mutableListOf<VersionSection>()
    var introTitle = "Novedades"
    val introDescBuilder = StringBuilder()
    var hasEncounteredFirstVersion = false

    var currentHeading = ""
    val currentContent = StringBuilder()

    fun flushSection() {
        if (currentHeading.isNotBlank()) {
            val (ver, date, sub) = extractVersionInfo(currentHeading)
            sections.add(
                VersionSection(
                    version = ver,
                    date = date,
                    subtitle = sub,
                    fullTitle = currentHeading,
                    content = currentContent.toString().trim(),
                    isLatest = sections.isEmpty()
                )
            )
            currentContent.clear()
        }
    }

    rawText.lines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.startsWith("## ")) {
            hasEncounteredFirstVersion = true
            flushSection()
            currentHeading = trimmed.removePrefix("## ").trim()
        } else if (trimmed.startsWith("# ") && !hasEncounteredFirstVersion) {
            introTitle = trimmed.removePrefix("# ").trim()
        } else if (!hasEncounteredFirstVersion) {
            if (trimmed.isNotBlank() && !trimmed.startsWith("---")) {
                introDescBuilder.append(trimmed).append(" ")
            }
        } else {
            if (currentHeading.isNotEmpty()) {
                if (!trimmed.startsWith("---")) {
                    currentContent.append(line).append("\n")
                }
            }
        }
    }
    flushSection()

    return ParsedChangelog(
        introTitle = introTitle,
        introDescription = introDescBuilder.toString().trim(),
        sections = sections
    )
}

private fun extractVersionInfo(heading: String): Triple<String, String?, String?> {
    var version = "v"
    var date: String? = null
    var subtitle: String? = null

    val versionRegex = """([vV]?\d+\.\d+(?:\.\d+)?)""".toRegex()
    val dateRegex = """\(([^)]+)\)""".toRegex()

    val verMatch = versionRegex.find(heading)
    if (verMatch != null) {
        version = verMatch.value
    }

    val dateMatch = dateRegex.find(heading)
    if (dateMatch != null) {
        date = dateMatch.groupValues[1].trim()
    }

    val dashIndex = heading.indexOf(" - ")
    if (dashIndex != -1 && dashIndex + 3 < heading.length) {
        subtitle = heading.substring(dashIndex + 3).trim().removeSurrounding("*").removeSurrounding("_").trim()
    } else if (version == "v") {
        subtitle = heading
    }

    return Triple(version, date, subtitle)
}

@Composable
fun ChangelogTimelineView(
    rawChangelog: String,
    modifier: Modifier = Modifier
) {
    val parsedData = remember(rawChangelog) { parseChangelog(rawChangelog) }

    if (parsedData.sections.isEmpty()) {
        // Fallback to straight markdown rendering if no version sections were detected
        StoreMarkdown(
            content = rawChangelog,
            isCompact = false,
            modifier = modifier
        )
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (parsedData.introDescription.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = parsedData.introDescription,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            parsedData.sections.forEachIndexed { index, section ->
                ChangelogAccordionCard(
                    section = section,
                    initiallyExpanded = (index == 0)
                )
            }
        }
    }
}

@Composable
fun ChangelogAccordionCard(
    section: VersionSection,
    initiallyExpanded: Boolean = false
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val arrowRotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (section.isLatest) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                shape = RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (section.isLatest) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Text(
                                text = section.version,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        if (section.isLatest) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Text(
                                    text = "Reciente",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (section.date != null) {
                            Text(
                                text = section.date,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (section.subtitle != null) {
                        Text(
                            text = section.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Contraer" else "Expandir",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(arrowRotation).padding(start = 8.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    StoreMarkdown(
                        content = section.content,
                        isCompact = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
