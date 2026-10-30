package com.malina.seanimetvport.data.plugin.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * [OkHttp]-backed [PluginStoreRepository] implementation.
 *
 * Uses the same blocking OkHttp call pattern as [com.malina.seanimetvport.data.plugin.JsHttpBridge]:
 * [OkHttpClient.newCall] executes on [Dispatchers.IO] and the body is fully buffered
 * before the response is closed. The whole operation is wrapped in [runCatching] so
 * any network or deserialization exception is mapped to [Result.failure].
 *
 * [MANIFEST_URL] is a placeholder constant — replace with the real URL when the
 * plugin store is deployed.
 */
class PluginStoreRepositoryImpl(private val okHttpClient: OkHttpClient) : PluginStoreRepository {

    companion object {
        const val MANIFEST_URL = "https://example.com/plugins/manifest.json"
    }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetchCatalogue(): Result<PluginStoreManifest> = withContext(Dispatchers.IO) {
        runCatching {
            okHttpClient.newCall(Request.Builder().url(MANIFEST_URL).build()).execute().use { response ->
                val body = response.body?.string() ?: error("Empty response body")
                json.decodeFromString<PluginStoreManifest>(body)
            }
        }
    }
}
