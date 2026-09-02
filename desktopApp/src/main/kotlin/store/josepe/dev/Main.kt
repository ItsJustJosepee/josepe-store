package store.josepe.dev

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import store.josepe.dev.data.repository.GitHubStoreRepository
import store.josepe.dev.platform.AppInstaller
import store.josepe.dev.ui.components.CustomTitleBar
import store.josepe.dev.ui.screens.CatalogScreen
import store.josepe.dev.ui.theme.JosepeStoreTheme
import store.josepe.dev.viewmodel.StoreViewModel

fun main() {
    System.setProperty("compose.interop.blending", "true")

    application {
        val repository = remember { GitHubStoreRepository() }
        val installer = remember { AppInstaller() }
        val updateManager = remember { store.josepe.dev.data.manager.StoreUpdateManager() }
        val viewModel = remember { StoreViewModel(repository, installer, updateManager, isAndroid = false) }

        val windowState = rememberWindowState(width = 1060.dp, height = 740.dp)

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "Josepe Store",
            undecorated = true,
            transparent = true,
        ) {
            val awtWindow = this.window

            DisposableEffect(Unit) {
                WindowsWindowProcSubclass.install(awtWindow)
                onDispose {
                    WindowsWindowProcSubclass.uninstall(awtWindow)
                }
            }

            LaunchedEffect(Unit) {
                awtWindow.toFront()
                awtWindow.requestFocus()
            }

            JosepeStoreTheme {
                val isMaximized = windowState.placement == WindowPlacement.Maximized
                val windowShape = if (isMaximized) {
                    RectangleShape
                } else {
                    RoundedCornerShape(12.dp)
                }
                val surfaceModifier = if (isMaximized) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxSize()
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), windowShape)
                }

                Surface(
                    modifier = surfaceModifier,
                    shape = windowShape,
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        CustomTitleBar(
                            windowState = windowState,
                            onClose = ::exitApplication,
                            title = "Josepe Store"
                        )
                        CatalogScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
