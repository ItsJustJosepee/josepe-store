package store.josepe.dev.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubRepo(
    val id: Long,
    val name: String,
    val description: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("stargazers_count") val stars: Int = 0,
    val fork: Boolean = false,
)

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
data class GitHubAsset(
    val name: String,
    val size: Long = 0,
    @SerialName("browser_download_url") val downloadUrl: String,
    @SerialName("content_type") val contentType: String? = null,
)

// Firestore REST Models
@Serializable
data class FirestoreProjectsResponse(
    val documents: List<FirestoreDocument> = emptyList()
)

@Serializable
data class FirestoreDocument(
    val name: String = "",
    val fields: Map<String, FirestoreValue> = emptyMap()
)

@Serializable
data class FirestoreValue(
    val stringValue: String? = null,
    val booleanValue: Boolean? = null,
    val integerValue: String? = null,
    val arrayValue: FirestoreArrayValue? = null
)

@Serializable
data class FirestoreArrayValue(
    val values: List<FirestoreValue> = emptyList()
)

// Josepe Dev Vercel API Response
@Serializable
data class JosepeDevStoreApiResponse(
    val success: Boolean = false,
    val source: String? = null,
    val apps: List<JosepeDevAppDto> = emptyList()
)

@Serializable
data class JosepeDevAppDto(
    val id: String,
    val title: String,
    val description: String = "",
    val clearDescription: String = "",
    val changelog: String = "",
    val changelogUrl: String? = null,
    val iconUrl: String? = null,
    val webUrl: String? = null,
    val mobileUrl: String? = null,
    val githubRepo: String? = null,
    val tags: List<String> = emptyList(),
    val status: String = "active"
)

data class StoreApp(
    val id: String,
    val repoName: String,
    val displayName: String,
    val description: String,
    val latestVersion: String,
    val releaseDate: String,
    val changelog: String,
    val iconUrl: String? = null,
    val tags: List<String> = emptyList(),
    val webUrl: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
    val repoUrl: String = "",
    val status: String = "active",
    val isInstalled: Boolean = false,
    val installedVersion: String? = null,
) {
    val hasNativeBuilds: Boolean
        get() = assets.isNotEmpty()

    val isUpdateAvailable: Boolean
        get() {
            if (!isInstalled || !hasNativeBuilds) return false
            val installed = installedVersion ?: return false
            return isNewerVersion(latestVersion, installed)
        }
}

fun isNewerVersion(remote: String, local: String): Boolean {
    if (remote.trim().equals(local.trim(), ignoreCase = true)) return false

    val rBase = remote.substringBefore("-").trim()
    val lBase = local.substringBefore("-").trim()

    val rParts = rBase.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
    val lParts = lBase.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
    val maxLen = maxOf(rParts.size, lParts.size)

    for (i in 0 until maxLen) {
        val r = rParts.getOrElse(i) { 0 }
        val l = lParts.getOrElse(i) { 0 }
        if (r > l) return true
        if (r < l) return false
    }

    val rBuild = remote.substringAfter("build.", "").filter { it.isDigit() }.toIntOrNull()
    val lBuild = local.substringAfter("build.", "").filter { it.isDigit() }.toIntOrNull()

    if (rBuild != null && lBuild != null) {
        return rBuild > lBuild
    }
    if (rBuild != null && lBuild == null) {
        return true
    }

    return false
}

sealed interface DownloadProgress {
    data object Idle : DownloadProgress
    data class Downloading(val progress: Float, val bytesRead: Long, val totalBytes: Long) : DownloadProgress
    data object Installing : DownloadProgress
    data object Completed : DownloadProgress
    data class Error(val message: String) : DownloadProgress
}
