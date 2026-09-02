package store.josepe.dev.data.repository

import store.josepe.dev.data.model.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class GitHubStoreRepository(
    private val client: HttpClient = createDefaultHttpClient()
) {
    companion object {
        private const val OWNER = "ItsJustJosepee"
        private const val GITHUB_BASE_URL = "https://api.github.com"
        private const val FIRESTORE_PROJECTS_URL =
            "https://firestore.googleapis.com/v1/projects/josepedev-data/databases/(default)/documents/projects"

        fun createDefaultHttpClient(): HttpClient {
            return HttpClient {
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    })
                }
                defaultRequest {
                    header("User-Agent", "JosepeStore-KMP/1.0.0")
                    header("Accept", "application/vnd.github.v3+json, application/json")
                }
            }
        }
    }

    /**
     * Fetches store catalog by combining:
     * 1. Official projects configured in Firestore (josepedev-data)
     * 2. Public GitHub releases and assets from ItsJustJosepee
     */
    suspend fun fetchStoreApps(isAndroid: Boolean): List<StoreApp> = withContext(Dispatchers.Default) {
        val appsMap = mutableMapOf<String, StoreApp>()

        // 1. Fetch from Firestore (josepedev official website database)
        try {
            val firestoreResponse: HttpResponse = client.get(FIRESTORE_PROJECTS_URL)
            if (firestoreResponse.status.value in 200..299) {
                val firestoreData: FirestoreProjectsResponse = firestoreResponse.body()
                
                for (doc in firestoreData.documents) {
                    val fields = doc.fields
                    val id = fields["id"]?.stringValue ?: doc.name.substringAfterLast("/")
                    val title = fields["title"]?.stringValue ?: id
                    val desc = fields["clearDescription"]?.stringValue
                        ?: fields["description"]?.stringValue
                        ?: ""
                    val iconUrl = fields["iconUrl"]?.stringValue
                    val webUrl = fields["webUrl"]?.stringValue?.takeIf { it.isNotEmpty() }
                        ?: fields["url"]?.stringValue?.takeIf { it.startsWith("http") }
                    val tags = fields["tags"]?.arrayValue?.values?.mapNotNull { it.stringValue } ?: emptyList()
                    val status = fields["status"]?.stringValue ?: "active"
                    val githubRepo = fields["githubRepo"]?.stringValue
                    val changelogUrl = fields["changelogUrl"]?.stringValue

                    var latestVersion = "1.0.0"
                    var releaseDate = ""
                    var changelog = ""
                    var assets = emptyList<GitHubAsset>()
                    var repoUrl = ""

                    // Fetch real-time GitHub release assets if repo is configured
                    if (!githubRepo.isNullOrEmpty()) {
                        val repoName = githubRepo.substringAfterLast("/")
                        repoUrl = "https://github.com/$githubRepo"
                        val release = fetchLatestRelease(repoName)
                        if (release != null) {
                            latestVersion = release.tagName.removePrefix("v")
                            releaseDate = release.publishedAt?.take(10) ?: ""
                            assets = filterAssetsForPlatform(release.assets, isAndroid)
                            changelog = release.body?.trim().orEmpty()
                        }
                    }

                    // If changelog is empty and changelogUrl exists, fetch the raw markdown changelog
                    if (changelog.isEmpty() && !changelogUrl.isNullOrEmpty()) {
                        changelog = fetchRawMarkdown(changelogUrl) ?: ""
                    }

                    if (changelog.isEmpty()) {
                        changelog = desc
                    }

                    val app = StoreApp(
                        id = id,
                        repoName = githubRepo?.substringAfterLast("/") ?: id,
                        displayName = title,
                        description = desc,
                        latestVersion = latestVersion,
                        releaseDate = releaseDate,
                        changelog = changelog,
                        iconUrl = iconUrl,
                        tags = tags,
                        webUrl = webUrl,
                        assets = assets,
                        repoUrl = repoUrl,
                        status = status
                    )
                    appsMap[app.repoName.lowercase()] = app
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Fetch additional GitHub repos that might not be in Firestore yet
        try {
            val reposResponse: HttpResponse = client.get("$GITHUB_BASE_URL/users/$OWNER/repos?sort=updated&per_page=50")
            if (reposResponse.status.value in 200..299) {
                val repos: List<GitHubRepo> = reposResponse.body()
                val candidateRepos = repos.filter { !it.fork }

                for (repo in candidateRepos) {
                    val key = repo.name.lowercase()
                    if (appsMap.containsKey(key)) continue // Already loaded from Firestore

                    val release = fetchLatestRelease(repo.name) ?: continue
                    val targetAssets = filterAssetsForPlatform(release.assets, isAndroid)
                    
                    if (targetAssets.isNotEmpty()) {
                        val cleanTitle = formatAppName(repo.name)
                        appsMap[key] = StoreApp(
                            id = repo.name,
                            repoName = repo.name,
                            displayName = cleanTitle,
                            description = repo.description ?: "Aplicación oficial del ecosistema Josepe Dev.",
                            latestVersion = release.tagName.removePrefix("v"),
                            releaseDate = release.publishedAt?.take(10) ?: "",
                            changelog = release.body?.trim() ?: "Sin notas de versión disponibles.",
                            assets = targetAssets,
                            repoUrl = repo.htmlUrl
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback: If nothing was fetched, provide Josepe Chat fallback
        if (appsMap.isEmpty()) {
            val fallback = getFallbackJosepeChatApp()
            appsMap[fallback.repoName.lowercase()] = fallback
        }

        // Return apps sorted: featured first, then with native builds, then others
        appsMap.values.sortedWith(
            compareByDescending<StoreApp> { it.status == "featured" }
                .thenByDescending { it.hasNativeBuilds }
                .thenBy { it.displayName }
        )
    }

    suspend fun fetchLatestRelease(repoName: String): GitHubRelease? {
        return try {
            val response: HttpResponse = client.get("$GITHUB_BASE_URL/repos/$OWNER/$repoName/releases/latest")
            if (response.status.value in 200..299) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchRawMarkdown(url: String): String? {
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.value in 200..299) {
                response.bodyAsText()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun filterAssetsForPlatform(assets: List<GitHubAsset>, isAndroid: Boolean): List<GitHubAsset> {
        return if (isAndroid) {
            assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        } else {
            assets.filter {
                it.name.endsWith(".deb", ignoreCase = true) ||
                it.name.endsWith(".msi", ignoreCase = true) ||
                it.name.endsWith(".dmg", ignoreCase = true) ||
                it.name.endsWith(".AppImage", ignoreCase = true) ||
                it.name.endsWith(".msix", ignoreCase = true)
            }
        }
    }

    private fun formatAppName(repoName: String): String {
        return when (repoName.lowercase()) {
            "josepechat", "josepe-chat", "josepe-chat-app" -> "Josepe Chat"
            "jepulse" -> "Jepulse"
            "josepe-store" -> "Josepe Store"
            "jmusic" -> "Joseph Music"
            else -> repoName.replace("-", " ").replace("_", " ")
                .split(" ")
                .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        }
    }

    private fun getFallbackJosepeChatApp(): StoreApp {
        return StoreApp(
            id = "jchat",
            repoName = "josepechat",
            displayName = "Josepe Chat",
            description = "Plataforma de mensajería Local-First y E2EE interoperable con PWA, Android y Desktop.",
            latestVersion = "1.6.2",
            releaseDate = "2026-09-02",
            changelog = "### Novedades v1.6.2\n- Separación en 4 arquitecturas nativas (arm64-v8a, armeabi-v7a, x86_64, x86).\n- Reducción del tamaño a la mitad (~60MB).\n- Optimizaciones de TLS y renderizado Markdown.",
            assets = emptyList(),
            repoUrl = "https://github.com/$OWNER/josepechat",
            status = "featured",
            webUrl = "https://chat.josepe.dev"
        )
    }
}
