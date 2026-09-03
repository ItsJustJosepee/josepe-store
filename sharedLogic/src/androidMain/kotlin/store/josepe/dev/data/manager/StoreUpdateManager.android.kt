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
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import store.josepe.dev.StoreConfig
import store.josepe.dev.data.model.DownloadProgress
import store.josepe.dev.data.model.GitHubAsset
import store.josepe.dev.data.model.GitHubRelease
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

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
                val preferredArchs = getDeviceSupportedArchitectures()
                val apkAsset = resolveBestMatchingApk(release.assets, preferredArchs)
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

            val downloadDir = context.externalCacheDir ?: context.getExternalFilesDir(null) ?: context.cacheDir
            val apkFile = File(downloadDir, "josepe-store-update.apk")
            if (apkFile.exists()) apkFile.delete()

            val okHttpClient = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(info.downloadUrl)
                .header("User-Agent", "JosepeStore-Android/${StoreConfig.VERSION_NAME}")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    _downloadProgress.value = DownloadProgress.Error("Error al descargar (HTTP ${response.code})")
                    return@withContext
                }

                val responseBody = response.body ?: throw IOException("Cuerpo de respuesta vacío")
                val totalBytes = responseBody.contentLength().takeIf { it > 0 } ?: info.assetSize

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
                                    _downloadProgress.value = DownloadProgress.Downloading(progress, bytesRead, totalBytes)
                                }
                            }
                        }
                        output.flush()
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

        return apks.firstOrNull { it.name.contains("universal", ignoreCase = true) || it.name.contains("-all", ignoreCase = true) } ?: apks.firstOrNull()
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
