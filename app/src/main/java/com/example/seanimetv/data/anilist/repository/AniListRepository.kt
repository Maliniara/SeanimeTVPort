package com.example.seanimetv.data.anilist.repository

import com.example.seanimetv.data.anilist.model.AiringScheduleEntry
import com.example.seanimetv.data.anilist.model.AniListFuzzyDate
import com.example.seanimetv.data.anilist.model.AniListMedia
import com.example.seanimetv.data.anilist.model.AniListMediaListEntry
import com.example.seanimetv.data.anilist.model.AniListUser
import com.example.seanimetv.data.anilist.model.MediaListStatus

interface AniListRepository {
    suspend fun getViewer(): Result<AniListUser>
    suspend fun getMediaList(userId: Int, status: MediaListStatus): Result<List<AniListMediaListEntry>>

    /**
     * All of the user's anime lists in ONE GraphQL request, grouped by status —
     * Home needs five statuses and per-status calls burn the AniList rate
     * limit (~30 req/min) five times faster.
     */
    suspend fun getMediaListCollection(
        userId: Int
    ): Result<Map<MediaListStatus, List<AniListMediaListEntry>>> =
        Result.failure(UnsupportedOperationException())

    suspend fun getMediaById(id: Int): Result<AniListMedia>
    suspend fun saveProgress(mediaId: Int, progress: Int, status: MediaListStatus): Result<Unit>
    suspend fun saveMediaListEntry(
        mediaId: Int,
        status: MediaListStatus,
        score: Double,
        progress: Int,
        repeat: Int = 0,
        startedAt: AniListFuzzyDate? = null,
        completedAt: AniListFuzzyDate? = null
    ): Result<Unit> = Result.failure(UnsupportedOperationException())
    suspend fun deleteMediaListEntry(entryId: Int, mediaId: Int): Result<Unit> =
        Result.failure(UnsupportedOperationException())
    suspend fun browseMedia(
        sort: List<String> = listOf("TRENDING_DESC"),
        genre: String? = null,
        seasonYear: Int? = null,
        season: String? = null,
        format: String? = null,
        status: String? = null,
        page: Int = 1
    ): Result<List<AniListMedia>> = Result.failure(UnsupportedOperationException())
    suspend fun getAiringSchedule(
        fromUnixSeconds: Int,
        toUnixSeconds: Int
    ): Result<List<AiringScheduleEntry>> = Result.failure(UnsupportedOperationException())
    suspend fun searchMedia(query: String, page: Int = 1): Result<List<AniListMedia>> =
        Result.failure(UnsupportedOperationException())
    suspend fun getCollectionAiringSchedule(
        mediaIds: List<Int>,
        fromUnixSeconds: Int,
        toUnixSeconds: Int
    ): Result<List<AiringScheduleEntry>> = Result.failure(UnsupportedOperationException())
}
