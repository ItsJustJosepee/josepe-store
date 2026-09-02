package store.josepe.dev

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import store.josepe.dev.data.repository.GitHubStoreRepository
import store.josepe.dev.platform.AppInstaller
import store.josepe.dev.ui.StoreApp
import store.josepe.dev.viewmodel.StoreViewModel

fun main() = application {
    val repository = GitHubStoreRepository()
    val installer = AppInstaller()
    val viewModel = StoreViewModel(repository, installer, isAndroid = false)

    val windowState = rememberWindowState(width = 960.dp, height = 700.dp)

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Josepe Store"
    ) {
        StoreApp(viewModel = viewModel)
    }
}
