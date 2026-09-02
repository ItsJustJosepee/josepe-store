package store.josepe.dev.di

import store.josepe.dev.data.repository.GitHubStoreRepository
import org.koin.dsl.module

val storeCommonModule = module {
    single { GitHubStoreRepository() }
}
