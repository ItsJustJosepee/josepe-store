package store.josepe.dev

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import store.josepe.dev.ui.StoreApp
import store.josepe.dev.viewmodel.StoreViewModel
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val viewModel: StoreViewModel by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StoreApp(viewModel = viewModel)
        }
    }
}
