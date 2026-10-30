package com.malina.seanimetvport.data.metadata

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/** Per-episode metadata for one episode number. */
data class AniZipEpisode(
    val title: String? = null,
    val image: String? = null,
    val overview: String? = null
)

/**
 * Fetches per-episode metadata (thumbnail, title, synopsis) from ani.zip —
 * the same aggregator Seanime uses to enrich provider episode lists. ani.zip
 * maps AniList IDs to TheTVDB episode data, so it fills in the distinct
 * thumbnails and descriptions that streaming providers omit.
 *
 * Endpoint: `https://api.ani.zip/mappings?anilist_id={id}` →
 * `{ "episodes": { "1": { title:{en}, image, overview, ... }, ... } }`.
 *
 * Failures degrade silently to an empty map — episode metadata is best-effort.
 */
class AniZipClient(private val okHttpClient: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    // Per-series in-memory cache: Continue Watching cards refetch on every
    // recompose otherwise, and the mapping rarely changes within a session.
    private val cache = java.util.concurrent.ConcurrentHashMap<Int, Map<Int, AniZipEpisode>>()

    suspend fun getEpisodes(anilistId: Int): Map<Int, AniZipEpisode> = withContext(Dispatchers.IO) {
        cache[anilistId]?.let { return@withContext it }
        runCatching {
            val request = Request.Builder()
                .url("https://api.ani.zip/mappings?anilist_id=$anilistId")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyMap<Int, AniZipEpisode>()
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use emptyMap<Int, AniZipEpisode>()
                val episodes = json.parseToJsonElement(body).jsonObject["episodes"]?.jsonObject
                    ?: return@use emptyMap<Int, AniZipEpisode>()
                buildMap {
                    episodes.forEach { (key, value) ->
                        val number = key.toIntOrNull() ?: return@forEach
                        val obj = value.jsonObject
                        val title = obj["title"]?.jsonObject?.get("en")?.jsonPrimitive?.contentOrNull
                        val image = obj["image"]?.jsonPrimitive?.contentOrNull
                        val overview = obj["overview"]?.jsonPrimitive?.contentOrNull
                        put(number, AniZipEpisode(title, image, overview))
                    }
                }
            }
        }.getOrDefault(emptyMap()).also { if (it.isNotEmpty()) cache[anilistId] = it }
    }
}
