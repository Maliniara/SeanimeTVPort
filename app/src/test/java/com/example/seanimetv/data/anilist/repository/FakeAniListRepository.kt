package com.example.seanimetv.data.anilist.repository

import com.example.seanimetv.data.anilist.model.AniListMedia
import com.example.seanimetv.data.anilist.model.AniListMediaListEntry
import com.example.seanimetv.data.anilist.model.AniListUser
import com.example.seanimetv.data.anilist.model.MediaListStatus

/**
 * Test double for [AniListRepository]. Configurable per-status media list results and
 * a call-argument recorder for verifying parallel dispatch.
 */
class FakeAniListRepository(
    private val viewerResult: Result<AniListUser> =
        Result.success(AniListUser(id = 1, name = "user")),
    private val mediaListByStatus: Map<MediaListStatus, Result<List<AniListMediaListEntry>>> =
        emptyMap(),
    private val defaultMediaListResult: Result<List<AniListMediaListEntry>> =
        Result.success(emptyList()),
    private val mediaByIdResult: Result<AniListMedia> =
        Result.failure(UnsupportedOperationException("not implemented in fake")),
    private val saveProgressResult: Result<Unit> = Result.success(Unit)
) : AniListRepository {

    /** Records every (userId, status) pair passed to [getMediaList]. */
    val mediaListCallArgs = mutableListOf<Pair<Int, MediaListStatus>>()

    override suspend fun getViewer(): Result<AniListUser> = viewerResult

    override suspend fun getMediaList(
        userId: Int,
        status: MediaListStatus
    ): Result<List<AniListMediaListEntry>> {
        mediaListCallArgs.add(userId to status)
        return mediaListByStatus[status] ?: defaultMediaListResult
    }

    override suspend fun getMediaById(id: Int): Result<AniListMedia> = mediaByIdResult

    override suspend fun saveProgress(
        mediaId: Int,
        progress: Int,
        status: MediaListStatus
    ): Result<Unit> = saveProgressResult
}
