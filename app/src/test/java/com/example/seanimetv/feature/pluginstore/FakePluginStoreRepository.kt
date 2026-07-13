package com.example.seanimetv.feature.pluginstore

import com.example.seanimetv.data.plugin.store.PluginStoreManifest
import com.example.seanimetv.data.plugin.store.PluginStoreRepository

/**
 * In-memory [PluginStoreRepository] implementation for JVM unit tests.
 *
 * Callers configure [result] before each test to control what [fetchCatalogue] returns.
 */
class FakePluginStoreRepository : PluginStoreRepository {
    var result: Result<PluginStoreManifest> = Result.success(PluginStoreManifest(emptyList()))

    override suspend fun fetchCatalogue(): Result<PluginStoreManifest> = result
}
