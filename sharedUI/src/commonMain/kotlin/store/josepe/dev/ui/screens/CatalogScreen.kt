package store.josepe.dev.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import store.josepe.dev.data.model.StoreApp
import store.josepe.dev.ui.components.AppCard
import store.josepe.dev.ui.components.AppDetailContent
import store.josepe.dev.ui.components.glassContainerColor
import store.josepe.dev.ui.components.glassEffect
import store.josepe.dev.ui.util.PlatformBackHandler
import store.josepe.dev.viewmodel.StoreViewModel

enum class StoreFilterCategory(val label: String) {
    ALL("Todas"),
    FEATURED("Destacadas"),
    NATIVE("Nativas"),
    WEB("Web Apps")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    viewModel: StoreViewModel,
    modifier: Modifier = Modifier
) {
    val apps by viewModel.apps.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedApp by viewModel.selectedApp.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()

    var selectedCategory by remember { mutableStateOf(StoreFilterCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val hazeState = remember { HazeState() }

    val filteredApps = remember(apps, selectedCategory, searchQuery) {
        apps.filter { app ->
            val matchesCategory = when (selectedCategory) {
                StoreFilterCategory.ALL -> true
                StoreFilterCategory.FEATURED -> app.status == "featured"
                StoreFilterCategory.NATIVE -> app.hasNativeBuilds
                StoreFilterCategory.WEB -> !app.hasNativeBuilds && app.webUrl != null
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                app.displayName.contains(searchQuery, ignoreCase = true) ||
                app.description.contains(searchQuery, ignoreCase = true) ||
                app.tags.any { it.contains(searchQuery, ignoreCase = true) }
            }
            matchesCategory && matchesSearch
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.F5 || (keyEvent.isCtrlPressed && keyEvent.key == Key.R))
                ) {
                    viewModel.loadCatalog()
                    true
                } else {
                    false
                }
            }
    ) {
        val screenWidth = maxWidth
        val isWideScreen = screenWidth >= 840.dp

        // On mobile / compact screens (< 840dp), if an app is selected, show full AppDetailScreen
        if (!isWideScreen && selectedApp != null) {
            val app = selectedApp!!
            AppDetailScreen(
                app = app,
                downloadProgress = downloadStates[app.repoName],
                onBack = { viewModel.selectApp(null) },
                onInstallClick = { viewModel.installApp(app) },
                onOpenClick = { viewModel.openApp(app) },
                onOpenWebClick = { viewModel.openWeb(app) }
            )
            return@BoxWithConstraints
        }

        // Wide layout (Desktop / Tablet): Master-Detail Row
        Row(modifier = Modifier.fillMaxSize()) {
            val columns = when {
                screenWidth < 640.dp -> GridCells.Fixed(1)
                screenWidth < 1100.dp -> GridCells.Fixed(2)
                else -> GridCells.Adaptive(minSize = 340.dp)
            }

            // Left Catalog Section
            Scaffold(
                topBar = {
                    Surface(
                        color = glassContainerColor(hazeState, MaterialTheme.colorScheme.surface, fallbackAlpha = 0.85f),
                        modifier = Modifier
                            .glassEffect(hazeState)
                            .statusBarsPadding()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            // Title bar row: App name + Platform Arch badge + Desktop Refresh button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Josepe Store",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = viewModel.currentArch,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                // Desktop / In-app animated refresh button
                                val infiniteTransition = rememberInfiniteTransition(label = "refresh")
                                val rotation by if (isLoading) {
                                    infiniteTransition.animateFloat(
                                        initialValue = 0f,
                                        targetValue = 360f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(900, easing = LinearEasing),
                                            repeatMode = RepeatMode.Restart
                                        ),
                                        label = "rotation"
                                    )
                                } else {
                                    remember { mutableStateOf(0f) }
                                }

                                IconButton(
                                    onClick = { viewModel.loadCatalog() },
                                    enabled = !isLoading,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Actualizar catálogo (F5)",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.rotate(rotation)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Search Bar
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text(
                                        "Buscar apps oficiales, utilidades, herramientas...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Buscar",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Limpiar",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
                                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Filter Chips row (Play Store style)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StoreFilterCategory.entries.forEach { category ->
                                    val isSelected = selectedCategory == category
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedCategory = category },
                                        label = {
                                            Text(
                                                text = category.label,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        shape = RoundedCornerShape(20.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) { paddingValues ->
                // Linear indicator always visible at top when loading
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    if (isLoading && apps.isNotEmpty()) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // PullToRefreshBox for touch gestures
                    PullToRefreshBox(
                        isRefreshing = isLoading,
                        onRefresh = { viewModel.loadCatalog() },
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                    ) {
                        if (isLoading && apps.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Cargando catálogo oficial desde josepe.dev...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = columns,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .hazeSource(state = hazeState),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Play Store Hero / Featured Banner
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    val gradientBrush = Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)
                                        )
                                    )
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        tonalElevation = 2.dp
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(gradientBrush)
                                                .padding(20.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Ecosistema Oficial Josepe Dev",
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = "Descarga de forma segura aplicaciones y herramientas oficiales directamente desde GitHub Releases y Vercel Edge con detección automática de CPU e historial de versiones detallado.",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    lineHeight = 20.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                if (filteredApps.isEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 48.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "No se encontraron aplicaciones en este criterio.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
                                    items(filteredApps, key = { it.id }) { app ->
                                        AppCard(
                                            app = app,
                                            downloadProgress = downloadStates[app.repoName],
                                            onCardClick = { viewModel.selectApp(app) },
                                            onInstallClick = { viewModel.installApp(app) },
                                            onOpenClick = { viewModel.openApp(app) },
                                            onOpenWebClick = { viewModel.openWeb(app) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Desktop Right-side Details Panel (Master-Detail)
            AnimatedVisibility(
                visible = isWideScreen && selectedApp != null,
                enter = slideInHorizontally { it } + fadeIn(),
                exit = slideOutHorizontally { it } + fadeOut()
            ) {
                selectedApp?.let { app ->
                    Surface(
                        modifier = Modifier
                            .width(440.dp)
                            .fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp
                    ) {
                        AppDetailContent(
                            app = app,
                            downloadProgress = downloadStates[app.repoName],
                            onInstallClick = { viewModel.installApp(app) },
                            onOpenClick = { viewModel.openApp(app) },
                            onOpenWebClick = { viewModel.openWeb(app) },
                            onClose = { viewModel.selectApp(null) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
