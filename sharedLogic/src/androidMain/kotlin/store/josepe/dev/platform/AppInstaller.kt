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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

actual class AppInstaller(
    private val context: Context,
    private val httpClient: HttpClient = HttpClient()
) {
    actual fun getDeviceArchitecture(): String {
        return getDeviceSupportedArchitectures().firstOrNull() ?: "arm64-v8a"
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
            val preferredArchs = getDeviceSupportedArchitectures()
            val matchingAsset = resolveBestMatchingApk(app.assets, preferredArchs)
            if (matchingAsset == null) {
                onProgress(DownloadProgress.Error("No se encontró un APK compatible con ${getDeviceArchitecture()}"))
                return@withContext
            }

            try {
                onProgress(DownloadProgress.Downloading(0f, 0L, matchingAsset.size))
                
                val cacheDir = context.externalCacheDir ?: context.cacheDir
                val apkFile = File(cacheDir, "${app.repoName}_${app.latestVersion}.apk")
                if (apkFile.exists()) apkFile.delete()

                val okHttpClient = OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url(matchingAsset.downloadUrl)
                    .header("User-Agent", "JosepeStore-Android")
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        onProgress(DownloadProgress.Error("Error al descargar (HTTP ${response.code})"))
                        return@withContext
                    }

                    val responseBody = response.body ?: throw IOException("Cuerpo de respuesta vacío")
                    val totalBytes = responseBody.contentLength().takeIf { it > 0 } ?: matchingAsset.size

                    responseBody.byteStream().use { input ->
                        apkFile.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var bytesRead = 0L
                            var read: Int
                            var lastProgressUpdate = 0L

                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                bytesRead += read

                                val now = System.currentTimeMillis()
                                if (now - lastProgressUpdate > 100 || bytesRead == totalBytes) {
                                    lastProgressUpdate = now
                                    if (totalBytes > 0) {
                                        val progress = (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                                        onProgress(DownloadProgress.Downloading(progress, bytesRead, totalBytes))
                                    }
                                }
                            }
                            output.flush()
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

    private fun normalizeAbi(rawAbi: String?): String? {
        val abi = rawAbi?.trim()?.lowercase() ?: return null
        return when {
            abi.contains("arm64") || abi.contains("aarch64") || abi.contains("armv8") -> "arm64-v8a"
            abi.contains("armeabi-v7a") || abi.contains("armv7") || (abi.contains("armeabi") && !abi.contains("v8")) -> "armeabi-v7a"
            abi.contains("x86_64") || abi.contains("x64") || abi.contains("amd64") -> "x86_64"
            abi.contains("x86") || abi.contains("i686") || abi.contains("i386") -> "x86"
            else -> null
        }
    }

    private fun getDeviceSupportedArchitectures(): List<String> {
        val candidates = mutableListOf<String>()

        Build.SUPPORTED_ABIS?.forEach { abi ->
            normalizeAbi(abi)?.let { if (!candidates.contains(it)) candidates.add(it) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_64_BIT_ABIS?.forEach { abi ->
                normalizeAbi(abi)?.let { if (!candidates.contains(it)) candidates.add(it) }
            }
            Build.SUPPORTED_32_BIT_ABIS?.forEach { abi ->
                normalizeAbi(abi)?.let { if (!candidates.contains(it)) candidates.add(it) }
            }
        }

        @Suppress("DEPRECATION")
        normalizeAbi(Build.CPU_ABI)?.let { if (!candidates.contains(it)) candidates.add(it) }
        @Suppress("DEPRECATION")
        normalizeAbi(Build.CPU_ABI2)?.let { if (!candidates.contains(it)) candidates.add(it) }

        normalizeAbi(System.getProperty("os.arch"))?.let { if (!candidates.contains(it)) candidates.add(it) }

        if (candidates.isEmpty()) {
            candidates.add("arm64-v8a")
        }
        return candidates
    }

    private fun resolveBestMatchingApk(assets: List<GitHubAsset>, preferredArchs: List<String>): GitHubAsset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apks.isEmpty()) return null

        // 1. Try matching device architecture priority, excluding universal APKs
        for (arch in preferredArchs) {
            val match = apks.firstOrNull { asset ->
                val name = asset.name.lowercase()
                val isUniversal = name.contains("universal") || name.contains("-all")
                if (isUniversal) return@firstOrNull false

                when (arch) {
                    "arm64-v8a" -> name.contains("arm64-v8a") || name.contains("arm64") || name.contains("aarch64")
                    "armeabi-v7a" -> name.contains("armeabi-v7a") || name.contains("armv7") || (name.contains("armeabi") && !name.contains("v8"))
                    "x86_64" -> name.contains("x86_64") || name.contains("x64")
                    "x86" -> (name.contains("x86") && !name.contains("x86_64")) || name.contains("x32")
                    else -> false
                }
            }
            if (match != null) return match
        }

        // 2. Fallback to universal APK if no CPU-specific APK matches
        return apks.firstOrNull { it.name.contains("universal", ignoreCase = true) || it.name.contains("-all", ignoreCase = true) } ?: apks.firstOrNull()
    }
}
