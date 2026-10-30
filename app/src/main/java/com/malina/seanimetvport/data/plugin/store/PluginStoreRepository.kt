package com.malina.seanimetvport.data.plugin.store

/**
 * Repository interface for fetching the remote plugin store catalogue.
 *
 * ViewModels depend on this interface, not the implementation, so the HTTP
 * details are isolated to [PluginStoreRepositoryImpl].
 */
interface PluginStoreRepository {

    /**
     * Fetches the plugin store manifest from the remote catalogue URL.
     *
     * @return [Result.success] with the parsed [PluginStoreManifest] on success,
     *         or [Result.failure] with the underlying exception on any network or
     *         deserialization error.
     */
    suspend fun fetchCatalogue(): Result<PluginStoreManifest>
}
