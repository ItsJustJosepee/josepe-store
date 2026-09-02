package store.josepe.dev.platform

import store.josepe.dev.data.model.DownloadProgress
import store.josepe.dev.data.model.StoreApp

expect class AppInstaller {
    suspend fun downloadAndInstall(app: StoreApp, onProgress: (DownloadProgress) -> Unit)
    fun isAppInstalled(packageName: String): Boolean
    fun getInstalledVersion(packageName: String): String?
    fun openInstalledApp(packageName: String)
    fun openWebUrl(url: String)
    fun getDeviceArchitecture(): String
}
