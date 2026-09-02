package store.josepe.dev.data.manager

import kotlinx.coroutines.flow.StateFlow
import store.josepe.dev.data.model.DownloadProgress

data class StoreUpdateInfo(
    val remoteVersion: String,
    val changelog: String,
    val downloadUrl: String,
    val assetSize: Long
)

expect class StoreUpdateManager {
    val updateAvailable: StateFlow<StoreUpdateInfo?>
    val downloadProgress: StateFlow<DownloadProgress>

    suspend fun checkForUpdate(): Boolean
    fun launchUpdate()
    fun dismiss()
}
