package store.josepe.dev.ui

import androidx.compose.runtime.Composable
import store.josepe.dev.ui.screens.CatalogScreen
import store.josepe.dev.ui.theme.JosepeStoreTheme
import store.josepe.dev.viewmodel.StoreViewModel

@Composable
fun StoreApp(
    viewModel: StoreViewModel
) {
    JosepeStoreTheme {
        CatalogScreen(viewModel = viewModel)
    }
}
