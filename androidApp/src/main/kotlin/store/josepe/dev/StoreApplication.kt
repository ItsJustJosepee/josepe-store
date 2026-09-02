package store.josepe.dev

import android.app.Application
import store.josepe.dev.data.repository.GitHubStoreRepository
import store.josepe.dev.di.storeCommonModule
import store.josepe.dev.platform.AppInstaller
import store.josepe.dev.viewmodel.StoreViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class StoreApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val androidModule = module {
            single { AppInstaller(androidContext()) }
            single { store.josepe.dev.data.manager.StoreUpdateManager(androidContext()) }
            single { StoreViewModel(get(), get(), get(), isAndroid = true) }
        }

        startKoin {
            androidContext(this@StoreApplication)
            modules(storeCommonModule, androidModule)
        }
    }
}
