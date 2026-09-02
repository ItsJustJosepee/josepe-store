package store.josepe.dev.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import store.josepe.dev.data.model.DownloadProgress
import store.josepe.dev.data.model.StoreApp
import store.josepe.dev.ui.components.AppDetailContent
import store.josepe.dev.ui.util.PlatformBackHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    app: StoreApp,
    downloadProgress: DownloadProgress?,
    onBack: () -> Unit,
    onInstallClick: () -> Unit,
    onOpenClick: () -> Unit,
    onOpenWebClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlatformBackHandler(enabled = true, onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = app.displayName,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        AppDetailContent(
            app = app,
            downloadProgress = downloadProgress,
            onInstallClick = onInstallClick,
            onOpenClick = onOpenClick,
            onOpenWebClick = onOpenWebClick,
            onClose = null,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
        )
    }
}
