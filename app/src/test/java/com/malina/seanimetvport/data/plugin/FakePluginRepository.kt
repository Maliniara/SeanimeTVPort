package com.malina.seanimetvport.data.plugin

import com.malina.seanimetvport.data.plugin.model.PluginEntry
import com.malina.seanimetvport.data.plugin.store.PluginStoreEntry

/**
 * In-memory [PluginRepository] implementation for JVM unit tests.
 *
 * Mirrors the behaviour of [PluginRepositoryImpl] without requiring Android DataStore:
 * - Built-in plugin is always first in the list and cannot be removed.
 * - Added plugins are stored in a mutable list (in-memory, not persisted).
 * - URL validation rejects non-HTTPS and non-.js URLs.
 * - Active plugin defaults to the built-in when not explicitly set.
 *
 * @param forceInstallFailure When true, [installPlugin] returns [Result.failure] for every
 *   non-built-in entry. Useful for testing the silent-failure path in PluginStoreViewModel.
 */
class FakePluginRepository(
    initialUserPlugins: List<PluginEntry> = emptyList(),
    initialActiveId: String? = null,
    val forceInstallFailure: Boolean = false
) : PluginRepository {

    /**
     * The id most recently passed to [setActivePlugin], or [initialActiveId] when not yet set.
     * Exposed as `internal` so ordering tests can read it from inside an `onPluginActivated`
     * lambda without requiring a suspend call.
     */
    internal var activeId: String? = initialActiveId
        private set

    private val userPlugins: MutableList<PluginEntry> = initialUserPlugins.toMutableList()

    override suspend fun getInstalledPlugins(): List<PluginEntry> =
        listOf(BUILTIN) + userPlugins

    override suspend fun addPlugin(url: String): Result<PluginEntry> {
        if (!url.startsWith("https://") || !url.endsWith(".js")) {
            return Result.failure(
                IllegalArgumentException("Plugin URL must use HTTPS and end with .js")
            )
        }
        val id = url.substringAfterLast("/").removeSuffix(".js")
        val entry = PluginEntry(id = id, name = id, assetPath = null, remoteUrl = url)
        userPlugins.add(entry)
        return Result.success(entry)
    }

    override suspend fun removePlugin(id: String) {
        if (id == BUILTIN.id) return // built-in is protected — silently ignore
        userPlugins.removeAll { it.id == id }
    }

    override suspend fun getActivePlugin(): PluginEntry? {
        val id = activeId ?: BUILTIN.id
        return getInstalledPlugins().find { it.id == id }
    }

    override suspend fun setActivePlugin(id: String) {
        activeId = id
    }

    /**
     * Installs a plugin from the store into the in-memory list.
     *
     * Idempotent by [PluginStoreEntry.id]: if a plugin with the same id is already
     * installed, returns the existing [PluginEntry] without duplicating it.
     * Returns [Result.failure] if [entry.id] matches the built-in plugin id, or if
     * [forceInstallFailure] is true (used by tests that exercise the silent-failure path).
     */
    override suspend fun installPlugin(entry: PluginStoreEntry): Result<PluginEntry> {
        if (entry.id == BUILTIN.id) return Result.failure(IllegalArgumentException("Cannot install built-in plugin"))
        if (forceInstallFailure) return Result.failure(IllegalStateException("Simulated install failure"))
        userPlugins.find { it.id == entry.id }?.let { return Result.success(it) }
        val newEntry = PluginEntry(
            id = entry.id,
            name = entry.name,
            assetPath = null,
            remoteUrl = entry.scriptUrl,
            version = entry.version,
            description = entry.description
        )
        userPlugins.add(newEntry)
        return Result.success(newEntry)
    }

    companion object {
        val BUILTIN = PluginEntry(
            id = "multistream-en",
            name = "Multistream EN",
            assetPath = "plugins/multistream-en.js",
            remoteUrl = null
        )
    }
}
