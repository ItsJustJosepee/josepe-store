package store.josepe.dev.data.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import store.josepe.dev.StoreConfig
import store.josepe.dev.data.model.DownloadProgress
import store.josepe.dev.data.model.GitHubAsset
import store.josepe.dev.data.model.GitHubRelease
import java.io.File

actual class StoreUpdateManager(
    private val context: Context,
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _updateAvailable = MutableStateFlow<StoreUpdateInfo?>(null)
    actual val updateAvailable: StateFlow<StoreUpdateInfo?> = _updateAvailable.asStateFlow()

    private val _downloadProgress = MutableStateFlow<DownloadProgress>(DownloadProgress.Idle)
    actual val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    actual suspend fun checkForUpdate(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/${StoreConfig.REPO_OWNER}/${StoreConfig.REPO_NAME}/releases/latest"
            val release = httpClient.get(url) {
                header("User-Agent", "JosepeStore-Android/${StoreConfig.VERSION_NAME}")
                header("Accept", "application/vnd.github.v3+json")
            }.body<GitHubRelease>()

            val remoteVersion = release.tagName.removePrefix("v").trim()
            val localVersion = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: StoreConfig.VERSION_NAME

            if (isNewerVersion(remoteVersion, localVersion)) {
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                if (apkAsset != null) {
                    val changelog = release.body?.trim() ?: "Nueva versión disponible."
                    _updateAvailable.value = StoreUpdateInfo(
                        remoteVersion = remoteVersion,
                        changelog = changelog,
                        downloadUrl = apkAsset.downloadUrl,
                        assetSize = apkAsset.size
                    )
                    return@withContext true
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    actual fun launchUpdate() {
        val info = _updateAvailable.value ?: return
        scope.launch {
            downloadAndInstall(info)
        }
    }

    actual fun dismiss() {
        _updateAvailable.value = null
        _downloadProgress.value = DownloadProgress.Idle
    }

    private suspend fun downloadAndInstall(info: StoreUpdateInfo) = withContext(Dispatchers.IO) {
        try {
            _downloadProgress.value = DownloadProgress.Downloading(0f, 0L, info.assetSize)
            val response = httpClient.get(info.downloadUrl)
            val totalBytes = response.contentLength() ?: info.assetSize

            val downloadDir = context.getExternalFilesDir(null) ?: context.filesDir
            val apkFile = File(downloadDir, "josepe-store-update.apk")
            if (apkFile.exists()) apkFile.delete()

            val channel = response.bodyAsChannel()
            channel.toInputStream().use { input ->
                apkFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Long = 0
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (totalBytes > 0) {
                            val progress = (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                            _downloadProgress.value = DownloadProgress.Downloading(progress, bytesRead, totalBytes)
                        }
                    }
                }
            }

            _downloadProgress.value = DownloadProgress.Installing
            installApk(apkFile)
        } catch (e: Exception) {
            _downloadProgress.value = DownloadProgress.Error(e.message ?: "Error al actualizar")
        }
    }

    private fun installApk(apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    _downloadProgress.value = DownloadProgress.Error("Concede permiso para instalar apps desconocidas y vuelve a presionar Actualizar.")
                    return
                }
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            _downloadProgress.value = DownloadProgress.Completed
        } catch (e: Exception) {
            _downloadProgress.value = DownloadProgress.Error("Error al lanzar instalador: ${e.message}")
        }
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val r = remote.substringBefore("-").split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val l = local.substringBefore("-").split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val maxLen = maxOf(r.size, l.size)
        for (i in 0 until maxLen) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv > lv) return true
            if (rv < lv) return false
        }
        return false
    }
}
