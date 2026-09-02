package store.josepe.dev.platform

import store.josepe.dev.data.model.DownloadProgress
import store.josepe.dev.data.model.GitHubAsset
import store.josepe.dev.data.model.StoreApp
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File

actual class AppInstaller(
    private val httpClient: HttpClient = HttpClient()
) {
    private val osName = System.getProperty("os.name")?.lowercase().orEmpty()
    private val isLinux = osName.contains("linux")
    private val isWindows = osName.contains("win")
    private val isMac = osName.contains("mac")

    actual fun getDeviceArchitecture(): String {
        return System.getProperty("os.arch") ?: "x86_64"
    }

    actual fun isAppInstalled(packageName: String): Boolean {
        return when {
            isLinux -> File("/opt/${resolveAppFolder(packageName)}").exists() || File("/usr/bin/${resolveAppFolder(packageName)}").exists()
            isWindows -> {
                val progFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
                File("$progFiles\\${resolveDisplayName(packageName)}").exists()
            }
            isMac -> File("/Applications/${resolveDisplayName(packageName)}.app").exists()
            else -> false
        }
    }

    actual fun getInstalledVersion(packageName: String): String? {
        return if (isAppInstalled(packageName)) "Instalado" else null
    }

    actual fun openInstalledApp(packageName: String) {
        try {
            when {
                isLinux -> {
                    val binary = File("/opt/${resolveAppFolder(packageName)}/bin/${resolveDisplayName(packageName)}")
                    if (binary.exists()) {
                        ProcessBuilder(binary.absolutePath).start()
                    } else {
                        ProcessBuilder("gtk-launch", resolveAppFolder(packageName)).start()
                    }
                }
                isWindows -> {
                    val progFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
                    val exe = File("$progFiles\\${resolveDisplayName(packageName)}\\${resolveDisplayName(packageName)}.exe")
                    if (exe.exists()) Desktop.getDesktop().open(exe)
                }
                isMac -> {
                    ProcessBuilder("open", "-a", resolveDisplayName(packageName)).start()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    actual fun openWebUrl(url: String) {
        try {
            Desktop.getDesktop().browse(java.net.URI(url))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    actual suspend fun downloadAndInstall(app: StoreApp, onProgress: (DownloadProgress) -> Unit) {
        withContext(Dispatchers.IO) {
            val matchingAsset = resolveBestDesktopAsset(app.assets)
            if (matchingAsset == null) {
                onProgress(DownloadProgress.Error("No se encontró instalador para este sistema operativo ($osName)"))
                return@withContext
            }

            try {
                onProgress(DownloadProgress.Downloading(0f, 0L, matchingAsset.size))
                val response = httpClient.get(matchingAsset.downloadUrl)
                val totalBytes = response.contentLength() ?: matchingAsset.size

                val tmpDir = File(System.getProperty("java.io.tmpdir"), "josepe_store_downloads")
                tmpDir.mkdirs()
                val installerFile = File(tmpDir, matchingAsset.name)
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
                                onProgress(DownloadProgress.Downloading(progress, bytesRead, totalBytes))
                            }
                        }
                    }
                }

                onProgress(DownloadProgress.Installing)
                launchInstaller(installerFile, onProgress)
            } catch (e: Exception) {
                onProgress(DownloadProgress.Error(e.message ?: "Error al descargar instalador"))
            }
        }
    }

    private fun launchInstaller(file: File, onProgress: (DownloadProgress) -> Unit) {
        try {
            if (isLinux) {
                ProcessBuilder("chmod", "+x", file.absolutePath).start().waitFor()
                if (file.name.endsWith(".deb", ignoreCase = true)) {
                    // Try graphical deb installers (gdebi, apturl, software-center) or open
                    val opened = runCatching {
                        ProcessBuilder("xdg-open", file.absolutePath).start()
                        true
                    }.getOrDefault(false)
                    if (!opened) Desktop.getDesktop().open(file)
                } else {
                    Desktop.getDesktop().open(file)
                }
            } else {
                Desktop.getDesktop().open(file)
            }
            onProgress(DownloadProgress.Completed)
        } catch (e: Exception) {
            onProgress(DownloadProgress.Error("Error al ejecutar instalador: ${e.message}"))
        }
    }

    private fun resolveBestDesktopAsset(assets: List<GitHubAsset>): GitHubAsset? {
        return when {
            isLinux -> assets.firstOrNull { it.name.endsWith(".deb", ignoreCase = true) }
                ?: assets.firstOrNull { it.name.endsWith(".AppImage", ignoreCase = true) }
            isWindows -> assets.firstOrNull { it.name.endsWith(".msi", ignoreCase = true) }
                ?: assets.firstOrNull { it.name.endsWith(".msix", ignoreCase = true) }
            isMac -> assets.firstOrNull { it.name.endsWith(".dmg", ignoreCase = true) }
            else -> null
        }
    }

    private fun resolveAppFolder(repoOrName: String): String {
        return when (repoOrName.lowercase()) {
            "josepechat", "josepe-chat", "josepe-chat-app" -> "josepe-chat"
            else -> repoOrName.lowercase()
        }
    }

    private fun resolveDisplayName(repoOrName: String): String {
        return when (repoOrName.lowercase()) {
            "josepechat", "josepe-chat", "josepe-chat-app" -> "Josepe Chat"
            else -> repoOrName
        }
    }
}
