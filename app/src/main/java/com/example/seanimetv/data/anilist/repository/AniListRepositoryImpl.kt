package com.example.seanimetv.data.anilist.repository

import com.example.seanimetv.data.anilist.AniListApiClient
import com.example.seanimetv.data.anilist.AniListQueries
import com.example.seanimetv.data.anilist.model.AiringScheduleEntry
import com.example.seanimetv.data.anilist.model.AniListFuzzyDate
import com.example.seanimetv.data.anilist.model.AniListMedia
import com.example.seanimetv.data.anilist.model.AniListMediaListEntry
import com.example.seanimetv.data.anilist.model.AniListUser
import com.example.seanimetv.data.anilist.model.MediaListStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.ConcurrentHashMap

class AniListRepositoryImpl(private val apiClient: AniListApiClient) : AniListRepository {

    private val json = Json { ignoreUnknownKeys = true }

    // ── In-memory TTL cache ──────────────────────────────────────────────
    // AniList's public API allows ~30 requests/minute. Without a cache every
    // section switch (Home → Search → Schedule → Browse) refires the same
    // queries and quickly trips HTTP 429. Successful results are cached with
    // a short TTL; saveProgress invalidates list/media entries it affects.
    private data class CacheEntry(val expiresAt: Long, val value: Any)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private companion object {
        const val LIST_TTL_MS = 5 * 60_000L
        const val MEDIA_TTL_MS = 5 * 60_000L
        const val SCHEDULE_TTL_MS = 10 * 60_000L
        const val VIEWER_TTL_MS = 30 * 60_000L
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(
        key: String,
        ttlMs: Long,
        fetch: suspend () -> Result<T>
    ): Result<T> {
        val now = System.currentTimeMillis()
        cache[key]?.let { entry ->
            if (entry.expiresAt > now) return Result.success(entry.value as T)
            cache.remove(key)
        }
        return fetch().onSuccess { value ->
            cache[key] = CacheEntry(now + ttlMs, value)
        }
    }

    private fun invalidate(prefix: String) {
        cache.keys.removeAll { it.startsWith(prefix) }
    }

    override suspend fun getViewer(): Result<AniListUser> =
        cached("viewer", VIEWER_TTL_MS) {
            apiClient.graphql(AniListQueries.VIEWER_QUERY).mapCatching { data ->
                val viewerElement = data["Viewer"]
                    ?: throw IllegalStateException("No Viewer field in response")
                json.decodeFromJsonElement<AniListUser>(viewerElement)
            }
        }

    override suspend fun getMediaList(
        userId: Int,
        status: MediaListStatus
    ): Result<List<AniListMediaListEntry>> =
        cached("mediaList:$userId:${status.name}", LIST_TTL_MS) {
            apiClient.graphql(
                AniListQueries.MEDIA_LIST_COLLECTION_QUERY,
                mapOf("userId" to userId, "type" to "ANIME", "status" to status.name)
            ).mapCatching { data ->
                val collection = data["MediaListCollection"]?.jsonObject
                    ?: return@mapCatching emptyList()
                val lists = collection["lists"]?.jsonArray
                    ?: return@mapCatching emptyList()
                if (lists.isEmpty()) return@mapCatching emptyList()
                val firstList = lists[0].jsonObject
                val entries = firstList["entries"]?.jsonArray
                    ?: return@mapCatching emptyList()
                json.decodeFromJsonElement<List<AniListMediaListEntry>>(entries)
            }
        }

    override suspend fun getMediaListCollection(
        userId: Int
    ): Result<Map<MediaListStatus, List<AniListMediaListEntry>>> =
        // Key shares the "mediaList:" prefix so saveProgress invalidation
        // (invalidate("mediaList:")) also evicts the combined collection.
        cached("mediaList:all:$userId", LIST_TTL_MS) {
            apiClient.graphql(
                AniListQueries.MEDIA_LIST_COLLECTION_QUERY,
                // No "status" variable: the client omits null variables and
                // AniList then returns ALL lists in one response.
                mapOf("userId" to userId, "type" to "ANIME")
            ).mapCatching { data ->
                val lists = data["MediaListCollection"]?.jsonObject
                    ?.get("lists")?.jsonArray
                    ?: return@mapCatching emptyMap()
                // Custom lists duplicate entries that also sit in a status
                // list — flatten, de-duplicate by entry id, group by status.
                val all = lists.flatMap { list ->
                    list.jsonObject["entries"]?.jsonArray?.let { entries ->
                        json.decodeFromJsonElement<List<AniListMediaListEntry>>(entries)
                    } ?: emptyList()
                }.distinctBy { it.id }
                all.groupBy { entry ->
                    runCatching { MediaListStatus.valueOf(entry.status) }.getOrNull()
                }.mapNotNull { (status, entries) ->
                    status?.let { it to entries }
                }.toMap()
            }
        }

    override suspend fun getMediaById(id: Int): Result<AniListMedia> =
        cached("media:$id", MEDIA_TTL_MS) {
            apiClient.graphql(
                AniListQueries.MEDIA_BY_ID_QUERY,
                mapOf("id" to id)
            ).mapCatching { data ->
                val mediaElement = data["Media"]
                    ?: throw IllegalStateException("No Media field in response")
                json.decodeFromJsonElement<AniListMedia>(mediaElement)
            }
        }

    override suspend fun saveProgress(
        mediaId: Int,
        progress: Int,
        status: MediaListStatus
    ): Result<Unit> =
        apiClient.graphql(
            AniListQueries.SAVE_MEDIA_LIST_ENTRY_MUTATION,
            mapOf("mediaId" to mediaId, "progress" to progress, "status" to status.name)
        ).map { }.onSuccess {
            // Progress changed on the server — cached lists and the media
            // entry (mediaListEntry.progress) are stale now.
            invalidate("mediaList:")
            invalidate("media:$mediaId")
        }

    override suspend fun saveMediaListEntry(
        mediaId: Int,
        status: MediaListStatus,
        score: Double,
        progress: Int,
        repeat: Int,
        startedAt: AniListFuzzyDate?,
        completedAt: AniListFuzzyDate?
    ): Result<Unit> =
        apiClient.graphql(
            AniListQueries.SAVE_MEDIA_LIST_ENTRY_DETAILS_MUTATION,
            mapOf(
                "mediaId" to mediaId,
                "status" to status.name,
                "score" to score,
                "progress" to progress,
                "repeat" to repeat,
                "startedAt" to startedAt?.let {
                    mapOf("year" to it.year, "month" to it.month, "day" to it.day)
                },
                "completedAt" to completedAt?.let {
                    mapOf("year" to it.year, "month" to it.month, "day" to it.day)
                }
            )
        ).map { }.onSuccess {
            invalidate("mediaList:")
            invalidate("media:$mediaId")
        }

    override suspend fun deleteMediaListEntry(entryId: Int, mediaId: Int): Result<Unit> =
        apiClient.graphql(
            AniListQueries.DELETE_MEDIA_LIST_ENTRY_MUTATION,
            mapOf("id" to entryId)
        ).map { }.onSuccess {
            invalidate("mediaList:")
            invalidate("media:$mediaId")
        }

    override suspend fun browseMedia(
        sort: List<String>,
        genre: String?,
        seasonYear: Int?,
        season: String?,
        format: String?,
        status: String?,
        page: Int
    ): Result<List<AniListMedia>> =
        cached("browse:$sort:$genre:$seasonYear:$season:$format:$status:$page", LIST_TTL_MS) {
            apiClient.graphql(
                AniListQueries.BROWSE_MEDIA_QUERY,
                mapOf(
                    "page" to page,
                    "perPage" to 30,
                    "sort" to sort,
                    "genre" to genre,
                    "seasonYear" to seasonYear,
                    "season" to season,
                    "format" to format,
                    "status" to status
                )
            ).mapCatching { data ->
                val pageObj = data["Page"]?.jsonObject ?: return@mapCatching emptyList()
                val media = pageObj["media"]?.jsonArray ?: return@mapCatching emptyList()
                json.decodeFromJsonElement<List<AniListMedia>>(media)
            }
        }

    override suspend fun getAiringSchedule(
        fromUnixSeconds: Int,
        toUnixSeconds: Int
    ): Result<List<AiringScheduleEntry>> =
        cached("airing:$fromUnixSeconds:$toUnixSeconds", SCHEDULE_TTL_MS) {
            fetchAiringSchedule(fromUnixSeconds, toUnixSeconds)
        }

    private suspend fun fetchAiringSchedule(
        fromUnixSeconds: Int,
        toUnixSeconds: Int
    ): Result<List<AiringScheduleEntry>> {
        return try {
            val all = mutableListOf<AiringScheduleEntry>()
            var currentPage = 1
            while (true) {
                currentCoroutineContext().ensureActive()
                val data = apiClient.graphql(
                    AniListQueries.AIRING_SCHEDULE_QUERY,
                    mapOf(
                        "airingAt_greater" to fromUnixSeconds,
                        "airingAt_lesser" to toUnixSeconds,
                        "page" to currentPage
                    )
                ).getOrThrow()
                val pageObj = data["Page"]?.jsonObject ?: break
                val schedules = pageObj["airingSchedules"]?.jsonArray ?: break
                val entries = json.decodeFromJsonElement<List<AiringScheduleEntry>>(schedules)
                all.addAll(entries)
                if (entries.size < 100 || currentPage >= 10) break
                currentPage++
                delay(250)
            }
            Result.success(all)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchMedia(query: String, page: Int): Result<List<AniListMedia>> =
        cached("search:$query:$page", LIST_TTL_MS) {
            apiClient.graphql(
                AniListQueries.SEARCH_MEDIA_QUERY,
                mapOf("search" to query, "page" to page, "perPage" to 30)
            ).mapCatching { data ->
                val pageObj = data["Page"]?.jsonObject ?: return@mapCatching emptyList()
                val media = pageObj["media"]?.jsonArray ?: return@mapCatching emptyList()
                json.decodeFromJsonElement<List<AniListMedia>>(media)
            }
        }

    override suspend fun getCollectionAiringSchedule(
        mediaIds: List<Int>,
        fromUnixSeconds: Int,
        toUnixSeconds: Int
    ): Result<List<AiringScheduleEntry>> =
        cached(
            "collectionAiring:${mediaIds.sorted().joinToString(",")}:$fromUnixSeconds:$toUnixSeconds",
            SCHEDULE_TTL_MS
        ) {
            fetchCollectionAiringSchedule(mediaIds, fromUnixSeconds, toUnixSeconds)
        }

    private suspend fun fetchCollectionAiringSchedule(
        mediaIds: List<Int>,
        fromUnixSeconds: Int,
        toUnixSeconds: Int
    ): Result<List<AiringScheduleEntry>> {
        if (mediaIds.isEmpty()) return Result.success(emptyList())
        return try {
            val all = mutableListOf<AiringScheduleEntry>()
            var currentPage = 1
            while (true) {
                currentCoroutineContext().ensureActive()
                val data = apiClient.graphql(
                    AniListQueries.COLLECTION_AIRING_SCHEDULE_QUERY,
                    mapOf(
                        "mediaId_in" to mediaIds,
                        "airingAt_greater" to fromUnixSeconds,
                        "airingAt_lesser" to toUnixSeconds,
                        "page" to currentPage
                    )
                ).getOrThrow()
                val pageObj = data["Page"]?.jsonObject ?: break
                val schedules = pageObj["airingSchedules"]?.jsonArray ?: break
                val entries = json.decodeFromJsonElement<List<AiringScheduleEntry>>(schedules)
                all.addAll(entries)
                if (entries.size < 50 || currentPage >= 5) break
                currentPage++
                delay(350)
            }
            Result.success(all)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
