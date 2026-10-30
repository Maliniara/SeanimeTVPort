package com.malina.seanimetvport.data.extension

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.malina.seanimetvport.data.plugin.PluginRepository
import com.malina.seanimetvport.data.plugin.TsTranspiler
import com.malina.seanimetvport.data.plugin.model.PluginEntry
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

private val Context.extensionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "extension_prefs"
)

/**
 * Marketplace-backed [ExtensionRepository].
 *
 * Mirrors the relevant Seanime server behaviours on-device:
 * - marketplace index: JSON array of [MarketplaceEntry] at a user-configurable URL
 * - per-extension manifest resolution ([ExtensionManifest]) with embedded or
 *   URI-referenced payloads
 * - `{{name}}` placeholder substitution from userConfig field defaults
 * - TypeScript payload transpilation (Sucrase in QuickJS, at install time)
 * - "@seanime-tv-compatible" payload marker → TV Ready badge
 */
class ExtensionRepositoryImpl(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val pluginRepository: PluginRepository,
    private val transpiler: TsTranspiler
) : ExtensionRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val REPO_URL_KEY = stringPreferencesKey("marketplace_repo_url")

    companion object {
        const val DEFAULT_REPO_URL =
            "https://raw.githubusercontent.com/Bas1874/Seanime-Marketplace/refs/heads/main/Marketplace/Main.json"
        const val TV_COMPAT_MARKER = "@seanime-tv-compatible"
        private const val EXTENSIONS_DIR = "extensions"
    }

    override suspend fun getRepoUrl(): String =
        context.extensionDataStore.data.map { it[REPO_URL_KEY] }.first() ?: DEFAULT_REPO_URL

    override suspend fun setRepoUrl(url: String) {
        context.extensionDataStore.edit { prefs -> prefs[REPO_URL_KEY] = url.trim() }
    }

    override suspend fun fetchMarketplace(): Result<List<MarketplaceEntry>> = runCatching {
        val body = httpGet(getRepoUrl())
        json.decodeFromString<List<MarketplaceEntry>>(body)
    }

    override suspend fun install(entry: MarketplaceEntry): Result<PluginEntry> = runCatching {
        // 1. Resolve the extension manifest (or synthesize one from the index
        //    entry when it only carries a direct payloadURI).
        val manifest: ExtensionManifest = when {
            !entry.manifestURI.isNullOrBlank() ->
                json.decodeFromString(httpGet(entry.manifestURI))

            !entry.payloadURI.isNullOrBlank() -> ExtensionManifest(
                id = entry.id,
                name = entry.name,
                version = entry.version,
                author = entry.author,
                description = entry.description,
                type = entry.type,
                language = entry.language,
                lang = entry.lang,
                icon = entry.icon,
                website = entry.website,
                payloadURI = entry.payloadURI
            )

            else -> error("Extension '${entry.name}' has no manifest or payload URI")
        }

        // 2. Resolve the raw payload.
        var payload = manifest.payload?.takeIf { it.isNotBlank() }
            ?: manifest.payloadURI?.takeIf { it.isNotBlank() }?.let { httpGet(it) }
            ?: entry.payloadURI?.takeIf { it.isNotBlank() }?.let { httpGet(it) }
            ?: error("Extension '${entry.name}' has an empty payload")

        // 3. Substitute {{placeholder}} values with userConfig defaults —
        //    Seanime injects the user's saved config here; on TV we apply the
        //    declared defaults.
        manifest.userConfig?.fields?.forEach { field ->
            payload = payload.replace("{{${field.name}}}", field.default ?: "")
        }

        // 4. TV compatibility marker — checked on the RAW payload, before any
        //    transpilation strips comments.
        val tvReady = payload.contains(TV_COMPAT_MARKER)

        // 5. Transpile TypeScript payloads to JavaScript.
        val language = (manifest.language ?: entry.language ?: "javascript").lowercase()
        val jsPayload = if (language == "typescript") {
            transpiler.transpile(payload).getOrThrow()
        } else {
            payload
        }

        // 6. Persist payload and register the plugin entry.
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, EXTENSIONS_DIR).apply { mkdirs() }
            File(dir, "${manifest.id}.js").apply { writeText(jsPayload) }
        }

        val pluginEntry = PluginEntry(
            id = manifest.id,
            name = manifest.name,
            filePath = file.absolutePath,
            version = manifest.version ?: entry.version,
            description = manifest.description ?: entry.description,
            author = manifest.author ?: entry.author,
            language = language,
            lang = manifest.lang ?: entry.lang,
            iconUrl = manifest.icon ?: entry.icon,
            typeStr = manifest.type ?: entry.type,
            website = manifest.website ?: entry.website,
            tvReady = tvReady
        )
        pluginRepository.installExtensionEntry(pluginEntry).getOrThrow()
    }

    override suspend fun checkUpdates(): Result<Map<String, String>> = runCatching {
        val marketplace = fetchMarketplace().getOrThrow().associateBy { it.id }
        val installed = pluginRepository.getInstalledPlugins()
        buildMap {
            installed.forEach { plugin ->
                val remote = marketplace[plugin.id] ?: return@forEach
                val remoteVersion = remote.version ?: return@forEach
                if (plugin.version != null && plugin.version != remoteVersion) {
                    put(plugin.id, remoteVersion)
                }
            }
        }
    }

    private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
        val response = okHttpClient.newCall(Request.Builder().url(url).build()).execute()
        response.use {
            if (!it.isSuccessful) throw IOException("HTTP ${it.code} for $url")
            it.body?.string() ?: throw IOException("Empty body for $url")
        }
    }
}
