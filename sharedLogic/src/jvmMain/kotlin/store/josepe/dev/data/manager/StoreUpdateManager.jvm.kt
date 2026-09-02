package store.josepe.dev.data.manager

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
import java.awt.Desktop
import java.io.File

actual class StoreUpdateManager(
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val osName = System.getProperty("os.name")?.lowercase().orEmpty()
    private val isLinux = osName.contains("linux")
    private val isWindows = osName.contains("win")
    private val isMac = osName.contains("mac")

    private val _updateAvailable = MutableStateFlow<StoreUpdateInfo?>(null)
    actual val updateAvailable: StateFlow<StoreUpdateInfo?> = _updateAvailable.asStateFlow()

    private val _downloadProgress = MutableStateFlow<DownloadProgress>(DownloadProgress.Idle)
    actual val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    actual suspend fun checkForUpdate(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/${StoreConfig.REPO_OWNER}/${StoreConfig.REPO_NAME}/releases/latest"
            val release = httpClient.get(url) {
                header("User-Agent", "JosepeStore-Desktop/${StoreConfig.VERSION_NAME}")
                header("Accept", "application/vnd.github.v3+json")
            }.body<GitHubRelease>()

            val remoteVersion = release.tagName.removePrefix("v").trim()
            val localVersion = StoreConfig.VERSION_NAME

            if (isNewerVersion(remoteVersion, localVersion)) {
                val asset = resolveDesktopAsset(release.assets)
                if (asset != null) {
                    val changelog = release.body?.trim() ?: "Nueva versión disponible."
                    _updateAvailable.value = StoreUpdateInfo(
                        remoteVersion = remoteVersion,
                        changelog = changelog,
                        downloadUrl = asset.downloadUrl,
                        assetSize = asset.size
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

            val tmpDir = File(System.getProperty("java.io.tmpdir"), "josepe_store_update")
            tmpDir.mkdirs()
            val fileName = info.downloadUrl.substringAfterLast("/")
            val installerFile = File(tmpDir, fileName)
            if (installerFile.exists()) installerFile.delete()

            val channel = response.bodyAsChannel()
            channel.toInputStream().use { input ->
                installerFile.outputStream().use { output ->
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

            if (!isWindows) {
                installerFile.setExecutable(true)
            }

            _downloadProgress.value = DownloadProgress.Installing
            launchInstaller(installerFile)
        } catch (e: Exception) {
            _downloadProgress.value = DownloadProgress.Error(e.message ?: "Error al actualizar")
        }
    }

    private fun launchInstaller(file: File) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file)
            } else {
                when {
                    isWindows -> Runtime.getRuntime().exec(arrayOf("cmd.exe", "/c", "start", file.absolutePath))
                    isMac -> Runtime.getRuntime().exec(arrayOf("open", file.absolutePath))
                    else -> Runtime.getRuntime().exec(arrayOf("xdg-open", file.absolutePath))
                }
            }
            _downloadProgress.value = DownloadProgress.Completed
        } catch (e: Exception) {
            _downloadProgress.value = DownloadProgress.Error("No se pudo iniciar instalador: ${e.message}")
        }
    }

    private fun resolveDesktopAsset(assets: List<GitHubAsset>): GitHubAsset? {
        return when {
            isLinux -> assets.firstOrNull { it.name.endsWith(".deb", ignoreCase = true) }
                ?: assets.firstOrNull { it.name.endsWith(".AppImage", ignoreCase = true) }
            isWindows -> assets.firstOrNull { it.name.endsWith(".msi", ignoreCase = true) }
            isMac -> assets.firstOrNull { it.name.endsWith(".dmg", ignoreCase = true) }
            else -> null
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
