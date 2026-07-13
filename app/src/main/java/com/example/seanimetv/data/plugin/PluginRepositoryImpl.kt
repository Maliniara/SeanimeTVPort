package com.example.seanimetv.data.plugin

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.seanimetv.data.plugin.model.PluginEntry
import com.example.seanimetv.data.plugin.store.PluginStoreEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.pluginDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "plugin_prefs"
)

/**
 * [DataStore]-backed [PluginRepository] implementation.
 *
 * User-added plugins are stored as a JSON array in `plugin_prefs` under the key
 * `plugins_json`. The active plugin id is stored under `active_plugin_id`.
 *
 * There are no bundled plugins — providers come only from installed extensions.
 * There is no forced default provider: when no active plugin is selected,
 * [getActivePlugin] returns null and the engine loads nothing.
 *
 * JVM unit tests use [FakePluginRepository] (in-memory) instead of this class,
 * because DataStore requires an Android Context. Persistence behaviour is covered
 * by instrumentation tests.
 */
class PluginRepositoryImpl(private val context: Context) : PluginRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private val PLUGINS_KEY = stringPreferencesKey("plugins_json")
    private val ACTIVE_KEY = stringPreferencesKey("active_plugin_id")
    private val DISABLED_KEY = stringPreferencesKey("disabled_ids")

    override suspend fun getInstalledPlugins(): List<PluginEntry> {
        val disabled = loadDisabledIds()
        return loadUserPlugins().map { it.copy(enabled = it.id !in disabled) }
    }

    override suspend fun addPlugin(url: String): Result<PluginEntry> {
        if (!url.startsWith("https://") || !url.endsWith(".js")) {
            return Result.failure(
                IllegalArgumentException("Plugin URL must use HTTPS and end with .js")
            )
        }
        val id = url.substringAfterLast("/").removeSuffix(".js")
        val existing = loadUserPlugins()
        existing.find { it.id == id }?.let { return Result.success(it) }
        val entry = PluginEntry(id = id, name = id, assetPath = null, remoteUrl = url)
        saveUserPlugins(existing + entry)
        return Result.success(entry)
    }

    override suspend fun removePlugin(id: String) {
        val plugins = loadUserPlugins()
        // Clean up the local payload file for marketplace-installed extensions
        plugins.find { it.id == id }?.filePath?.let { path ->
            runCatching { java.io.File(path).delete() }
        }
        saveUserPlugins(plugins.filter { it.id != id })
    }

    override suspend fun getActivePlugin(): PluginEntry? {
        val activeId = context.pluginDataStore.data
            .map { it[ACTIVE_KEY] }
            .first()
            ?: return null
        return getInstalledPlugins().find { it.id == activeId }
    }

    override suspend fun setActivePlugin(id: String) {
        context.pluginDataStore.edit { prefs -> prefs[ACTIVE_KEY] = id }
    }

    override suspend fun setPluginEnabled(id: String, enabled: Boolean) {
        val current = loadDisabledIds().toMutableSet()
        if (enabled) current.remove(id) else current.add(id)
        context.pluginDataStore.edit { prefs ->
            prefs[DISABLED_KEY] = json.encodeToString(current.toList())
        }
    }

    private suspend fun loadDisabledIds(): Set<String> {
        val jsonStr = context.pluginDataStore.data
            .map { it[DISABLED_KEY] }
            .first()
            ?: return emptySet()
        return try {
            json.decodeFromString<List<String>>(jsonStr).toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    override suspend fun installPlugin(entry: PluginStoreEntry): Result<PluginEntry> {
        val existing = loadUserPlugins()
        existing.find { it.id == entry.id }?.let { return Result.success(it) }
        val newEntry = PluginEntry(
            id = entry.id,
            name = entry.name,
            assetPath = null,
            remoteUrl = entry.scriptUrl,
            version = entry.version,
            description = entry.description
        )
        saveUserPlugins(existing + newEntry)
        return Result.success(newEntry)
    }

    override suspend fun installExtensionEntry(entry: PluginEntry): Result<PluginEntry> {
        // Upsert: updating an installed extension replaces its stored entry
        val others = loadUserPlugins().filter { it.id != entry.id }
        saveUserPlugins(others + entry)
        return Result.success(entry)
    }

    private suspend fun loadUserPlugins(): List<PluginEntry> {
        val jsonStr = context.pluginDataStore.data
            .map { it[PLUGINS_KEY] }
            .first()
            ?: return emptyList()
        return try {
            json.decodeFromString<List<PluginEntry>>(jsonStr)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun saveUserPlugins(plugins: List<PluginEntry>) {
        context.pluginDataStore.edit { prefs ->
            prefs[PLUGINS_KEY] = json.encodeToString(plugins)
        }
    }
}
