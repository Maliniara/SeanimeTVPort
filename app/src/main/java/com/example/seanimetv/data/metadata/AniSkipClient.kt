package com.example.seanimetv.data.metadata

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/** Which part of an episode a skip interval covers. */
enum class SkipType { INTRO, OUTRO }

/** A single skippable interval (opening or ending), in milliseconds. */
data class SkipInterval(
    val type: SkipType,
    val startMs: Long,
    val endMs: Long
)

/**
 * Fetches opening/ending skip timestamps from the community-sourced AniSkip API —
 * the same database MAL-Sync and most web players use. Keyed on MyAnimeList id +
 * episode number + episode length (for disambiguation).
 *
 * Endpoint: `https://api.aniskip.com/v2/skip-times/{malId}/{episode}?types=op&types=ed&episodeLength={sec}`
 * → `{ found, results: [{ skipType: "op"|"ed", interval: { startTime, endTime } }] }`
 * (times are seconds as doubles).
 *
 * Coverage is crowd-sourced: popular seasonal titles are well covered, obscure /
 * old ones often aren't. Any failure (no MAL id, no data, network) degrades
 * silently to an empty list — the Skip button simply never appears.
 */
class AniSkipClient(private val okHttpClient: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getSkipTimes(
        malId: Int,
        episodeNumber: Int,
        episodeLengthSec: Long
    ): List<SkipInterval> = withContext(Dispatchers.IO) {
        if (malId <= 0 || episodeNumber <= 0) return@withContext emptyList()
        runCatching {
            val url = "https://api.aniskip.com/v2/skip-times/$malId/$episodeNumber" +
                "?types=op&types=ed&episodeLength=$episodeLengthSec"
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList<SkipInterval>()
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use emptyList<SkipInterval>()
                val results = json.parseToJsonElement(body).jsonObject["results"]?.jsonArray
                    ?: return@use emptyList<SkipInterval>()
                results.mapNotNull { element ->
                    val obj = element.jsonObject
                    val type = when (obj["skipType"]?.jsonPrimitive?.content) {
                        "op", "mixed-op" -> SkipType.INTRO
                        "ed", "mixed-ed" -> SkipType.OUTRO
                        else -> return@mapNotNull null
                    }
                    val interval = obj["interval"]?.jsonObject ?: return@mapNotNull null
                    val start = interval["startTime"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                    val end = interval["endTime"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                    if (end <= start) return@mapNotNull null
                    SkipInterval(type, (start * 1000).toLong(), (end * 1000).toLong())
                }
            }
        }.getOrDefault(emptyList())
    }
}
