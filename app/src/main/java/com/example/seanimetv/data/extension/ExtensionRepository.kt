package com.example.seanimetv.data.extension

import com.example.seanimetv.data.plugin.model.PluginEntry

interface ExtensionRepository {
    /** Currently configured marketplace index URL. */
    suspend fun getRepoUrl(): String

    /** Persists a new marketplace index URL. */
    suspend fun setRepoUrl(url: String)

    /** Fetches and parses the marketplace index at the configured URL. */
    suspend fun fetchMarketplace(): Result<List<MarketplaceEntry>>

    /**
     * Installs (or updates) a marketplace extension: resolves the manifest,
     * downloads the payload, substitutes userConfig defaults, transpiles
     * TypeScript to JavaScript, persists the payload to a local file, and
     * registers the plugin entry.
     */
    suspend fun install(entry: MarketplaceEntry): Result<PluginEntry>

    /**
     * Returns ids of installed extensions for which the marketplace lists a
     * different (newer) version.
     */
    suspend fun checkUpdates(): Result<Map<String, String>>
}
