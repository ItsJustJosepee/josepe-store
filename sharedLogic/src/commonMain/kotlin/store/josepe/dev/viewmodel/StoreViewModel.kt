package store.josepe.dev.viewmodel

import store.josepe.dev.data.model.DownloadProgress
import store.josepe.dev.data.model.StoreApp
import store.josepe.dev.data.repository.GitHubStoreRepository
import store.josepe.dev.platform.AppInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StoreViewModel(
    private val repository: GitHubStoreRepository,
    private val installer: AppInstaller,
    private val isAndroid: Boolean
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _apps = MutableStateFlow<List<StoreApp>>(emptyList())
    val apps: StateFlow<List<StoreApp>> = _apps.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedApp = MutableStateFlow<StoreApp?>(null)
    val selectedApp: StateFlow<StoreApp?> = _selectedApp.asStateFlow()

    private val _downloadStates = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadProgress>> = _downloadStates.asStateFlow()

    val currentArch: String = installer.getDeviceArchitecture()

    init {
        loadCatalog()
    }

    fun loadCatalog() {
        scope.launch {
            _isLoading.value = true
            val fetched = repository.fetchStoreApps(isAndroid)
            val updated = fetched.map { app ->
                val installed = installer.isAppInstalled(app.repoName)
                val installedVer = installer.getInstalledVersion(app.repoName)
                app.copy(isInstalled = installed, installedVersion = installedVer)
            }
            _apps.value = updated
            _isLoading.value = false
        }
    }

    fun selectApp(app: StoreApp?) {
        _selectedApp.value = app
    }

    fun installApp(app: StoreApp) {
        scope.launch {
            installer.downloadAndInstall(app) { progress ->
                _downloadStates.update { current ->
                    current + (app.repoName to progress)
                }
                if (progress is DownloadProgress.Completed) {
                    // Refresh status
                    val isInstalled = installer.isAppInstalled(app.repoName)
                    _apps.update { list ->
                        list.map {
                            if (it.repoName == app.repoName) it.copy(isInstalled = isInstalled) else it
                        }
                    }
                }
            }
        }
    }

    fun openApp(app: StoreApp) {
        installer.openInstalledApp(app.repoName)
    }

    fun openWeb(app: StoreApp) {
        val url = app.webUrl ?: app.repoUrl
        if (url.isNotEmpty()) {
            installer.openWebUrl(url)
        }
    }
}
