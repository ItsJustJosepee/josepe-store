package store.josepe.dev.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
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
import java.io.File

actual class AppInstaller(
    private val context: Context,
    private val httpClient: HttpClient = HttpClient()
) {
    actual fun getDeviceArchitecture(): String {
        return Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
    }

    actual fun isAppInstalled(packageName: String): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(resolvePackageName(packageName), 0)
            true
        }.getOrDefault(false)
    }

    actual fun getInstalledVersion(packageName: String): String? {
        return runCatching {
            context.packageManager.getPackageInfo(resolvePackageName(packageName), 0).versionName
        }.getOrNull()
    }

    actual fun openInstalledApp(packageName: String) {
        val resolved = resolvePackageName(packageName)
        val intent = context.packageManager.getLaunchIntentForPackage(resolved)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    actual fun openWebUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    actual suspend fun downloadAndInstall(app: StoreApp, onProgress: (DownloadProgress) -> Unit) {
        withContext(Dispatchers.IO) {
            val matchingAsset = resolveBestMatchingApk(app.assets, Build.SUPPORTED_ABIS ?: emptyArray())
            if (matchingAsset == null) {
                onProgress(DownloadProgress.Error("No se encontró un APK compatible con ${getDeviceArchitecture()}"))
                return@withContext
            }

            try {
                onProgress(DownloadProgress.Downloading(0f, 0L, matchingAsset.size))
                val response = httpClient.get(matchingAsset.downloadUrl)
                val totalBytes = response.contentLength() ?: matchingAsset.size
                
                val cacheDir = context.externalCacheDir ?: context.cacheDir
                val apkFile = File(cacheDir, "${app.repoName}_${app.latestVersion}.apk")
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
                                onProgress(DownloadProgress.Downloading(progress, bytesRead, totalBytes))
                            }
                        }
                    }
                }

                onProgress(DownloadProgress.Installing)
                installApk(apkFile, onProgress)
            } catch (e: Exception) {
                onProgress(DownloadProgress.Error(e.message ?: "Error durante la descarga"))
            }
        }
    }

    private fun installApk(apkFile: File, onProgress: (DownloadProgress) -> Unit) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    onProgress(DownloadProgress.Error("Concede el permiso para instalar apps desconocidas y vuelve a presionar Instalar."))
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
            onProgress(DownloadProgress.Completed)
        } catch (e: Exception) {
            onProgress(DownloadProgress.Error("Error al abrir instalador: ${e.message}"))
        }
    }

    private fun resolvePackageName(repoOrName: String): String {
        return when (repoOrName.lowercase()) {
            "josepechat", "josepe-chat", "josepe-chat-app" -> "chat.josepe.dev"
            "jepulse" -> "dev.josepe.jepulse"
            "josepe-store", "josepestore", "josepe-store-app" -> "store.josepe.dev"
            else -> repoOrName
        }
    }

    private fun resolveBestMatchingApk(assets: List<GitHubAsset>, supportedAbis: Array<String>): GitHubAsset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apks.isEmpty()) return null

        for (abi in supportedAbis) {
            val match = apks.firstOrNull { asset ->
                val name = asset.name.lowercase()
                when (abi.lowercase()) {
                    "arm64-v8a" -> name.contains("arm64-v8a") || name.contains("arm64") || name.contains("aarch64")
                    "armeabi-v7a" -> name.contains("armeabi-v7a") || name.contains("armv7") || (name.contains("armeabi") && !name.contains("v8"))
                    "x86_64" -> name.contains("x86_64") || name.contains("x64")
                    "x86" -> (name.contains("x86") && !name.contains("x86_64")) || name.contains("x32")
                    else -> false
                }
            }
            if (match != null) return match
        }

        return apks.firstOrNull { it.name.contains("universal", ignoreCase = true) || it.name.contains("-all", ignoreCase = true) } ?: apks.firstOrNull()
    }
}
