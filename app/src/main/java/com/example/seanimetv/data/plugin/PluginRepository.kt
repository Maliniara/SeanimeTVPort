package com.example.seanimetv.data.plugin

import com.example.seanimetv.data.plugin.model.PluginEntry
import com.example.seanimetv.data.plugin.store.PluginStoreEntry

/**
 * Repository interface for managing installed JavaScript plugins.
 *
 * The built-in `multistream-en` plugin is always available and cannot be removed.
 * User-added plugins are persisted across sessions.
 *
 * All methods are suspend functions — implementations may perform DataStore or
 * network I/O. ViewModels depend on this interface, not the implementation.
 */
interface PluginRepository {

    /** Returns all installed plugins. The built-in plugin is always the first entry. */
    suspend fun getInstalledPlugins(): List<PluginEntry>

    /**
     * Downloads (or registers) a plugin from [url] and adds it to the installed list.
     *
     * @param url Must be an HTTPS URL ending in `.js`.
     * @return [Result.success] with the new [PluginEntry] on success,
     *         [Result.failure] with an [IllegalArgumentException] if the URL is invalid.
     */
    suspend fun addPlugin(url: String): Result<PluginEntry>

    /**
     * Removes a user-added plugin by [id].
     * If [id] equals `"multistream-en"` (the built-in), this method does nothing.
     */
    suspend fun removePlugin(id: String)

    /**
     * Returns the currently active plugin, or the built-in plugin if none has been
     * explicitly set. Returns `null` only if no plugins exist (impossible in practice).
     */
    suspend fun getActivePlugin(): PluginEntry?

    /** Persists [id] as the active plugin selection. */
    suspend fun setActivePlugin(id: String)

    /**
     * Enables or disables a plugin WITHOUT removing it. Disabled plugins keep their
     * installed entry but are excluded from the provider list (and autoprovider).
     * Reflected as [PluginEntry.enabled] on the entries returned by
     * [getInstalledPlugins]. Default no-op so test fakes need not implement it.
     */
    suspend fun setPluginEnabled(id: String, enabled: Boolean) {}

    /**
     * Installs a plugin from the store and persists it to the installed list.
     *
     * Install only — does not activate the plugin. The active plugin is managed
     * separately via [setActivePlugin].
     *
     * Idempotent by [PluginStoreEntry.id]: if a plugin with the same id is already
     * installed, returns the existing [PluginEntry] without duplicating it.
     *
     * @return [Result.failure] with [IllegalArgumentException] if [entry.id] equals
     *         `"multistream-en"` (the built-in plugin is protected against install).
     */
    suspend fun installPlugin(entry: PluginStoreEntry): Result<PluginEntry>

    /**
     * Registers or updates (upsert by id) a fully-resolved marketplace extension
     * entry — payload already downloaded, transpiled, and persisted to
     * [PluginEntry.filePath] by the extension repository.
     */
    suspend fun installExtensionEntry(entry: PluginEntry): Result<PluginEntry> =
        Result.failure(UnsupportedOperationException())
}
