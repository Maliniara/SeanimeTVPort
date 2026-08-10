package com.example.seanimetv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.seanimetv.data.anilist.model.AniListMedia
import com.example.seanimetv.data.anilist.model.AniListMediaListEntry
import com.example.seanimetv.data.anilist.model.MediaListStatus
import com.example.seanimetv.data.anilist.repository.AniListRepository
import com.example.seanimetv.data.history.WatchHistoryEntry
import com.example.seanimetv.data.history.WatchHistoryStore
import com.example.seanimetv.data.anilist.AccountRepository
import com.example.seanimetv.data.anilist.AniListAccount
import com.example.seanimetv.data.model.AnimeEntry
import com.example.seanimetv.data.net.toUserMessage
import com.example.seanimetv.data.settings.AnimeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(
        val currentList: List<AnimeEntry>,
        val planningList: List<AnimeEntry>,
        val completedList: List<AnimeEntry>,
        val droppedList: List<AnimeEntry>,
        val pausedList: List<AnimeEntry>,
        val recommendedList: List<AnimeEntry> = emptyList(),
        val stats: LibraryStats? = null
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

/**
 * Aggregate statistics over the viewer's whole AniList library, computed from the
 * raw list entries (Denshi-style "Stats" overview). All counts are display-ready;
 * [scoreDistribution] is a fixed 10-element list (buckets for normalised scores 1..10).
 */
data class LibraryStats(
    val totalAnime: Int,
    val episodesWatched: Int,
    val minutesWatched: Int,
    val meanScore: Double,
    val scoreDistribution: List<Int>,
    val statusCounts: List<Pair<String, Int>>,
    val topGenres: List<Pair<String, Int>>,
    val formatCounts: List<Pair<String, Int>>
) {
    /** Total watch time expressed in whole days (minutes / 1440). */
    val daysWatched: Double get() = minutesWatched / 1440.0
}

class HomeViewModel(
    private val aniListRepository: AniListRepository,
    private val watchHistoryStore: WatchHistoryStore? = null,
    private val animeSettingsStore: AnimeSettingsRepository? = null,
    private val accountRepository: AccountRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** Local playback history (last 10 series), reactive — updates live as the
     *  player records progress. Drives the Home "Recently watched" row. */
    val watchHistory: StateFlow<List<WatchHistoryEntry>> =
        (watchHistoryStore?.history(10) ?: flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Series the user hid from the Continue Watching row via the card menu. The
    // row is derived from the AniList "Watching" list, so hiding is a local,
    // session-only view filter — we deliberately do NOT touch the AniList status
    // (that's the separate "change status" action), and it isn't persisted so a
    // newly-aired episode can bring the show back on the next launch.
    private val _dismissedContinue = MutableStateFlow<Set<Int>>(emptySet())
    val dismissedContinue: StateFlow<Set<Int>> = _dismissedContinue.asStateFlow()

    /** Hides one series from the Continue Watching row (view-only, see above). */
    fun dismissFromContinue(anilistId: Int) {
        _dismissedContinue.value = _dismissedContinue.value + anilistId
    }

    /** Removes one series from the local "Recently watched" history for good. */
    fun removeFromHistory(anilistId: Int) {
        val store = watchHistoryStore ?: return
        viewModelScope.launch { store.remove(anilistId) }
    }

    /**
     * Moves a series to a new AniList list status (Watching/Completed/…), keeping
     * its current [progress], then refreshes so the Home rows reflect the change.
     */
    fun changeStatus(mediaId: Int, progress: Int, status: MediaListStatus) {
        viewModelScope.launch {
            aniListRepository.saveProgress(mediaId, progress, status)
            refresh()
        }
    }

    // NOTE: periodic refresh lives in HomeScreen behind repeatOnLifecycle —
    // a viewModelScope loop here would keep polling AniList forever while the
    // app sits in the background (TV boxes keep processes alive for days).

    /** Explicit (re)load — used on first entry and manual retry. */
    fun loadHomeData() = refresh()

    fun retry() = refresh()

    /**
     * Fetches and swaps in fresh data WITHOUT a Loading flash. A background
     * failure keeps the previous (stale) data on screen; only a failure with no
     * prior data surfaces an Error.
     */
    fun refresh() {
        viewModelScope.launch {
            when (val result = fetchHome()) {
                is HomeUiState.Success -> _uiState.value = result
                is HomeUiState.Error -> if (_uiState.value !is HomeUiState.Success) _uiState.value = result
                else -> Unit
            }
        }
    }

    /** True when the active account is the local-only Guest (no AniList). */
    private suspend fun isGuestActive(): Boolean {
        val repo = accountRepository ?: return false
        val activeId = repo.activeAccountId.first()
        return repo.listAccounts().find { it.id == activeId }?.isGuest == true
    }

    private suspend fun fetchHome(): HomeUiState {
        // Guest account: no AniList data — show an empty library. The local
        // "Recently watched" row still populates via the watchHistory flow.
        if (isGuestActive()) {
            return HomeUiState.Success(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        }
        val user = aniListRepository.getViewer()
            .getOrElse { return HomeUiState.Error(it.toUserMessage()) }
        // Record the active AniList account if it has no entry yet — covers
        // sessions migrated from the pre-multi-account token store (token but no
        // account record), so the account shows up in the picker & settings.
        accountRepository?.let { repo ->
            if (repo.listAccounts().none { it.id == user.id }) {
                repo.getToken()?.let { token ->
                    repo.addOrUpdateAccount(
                        AniListAccount(
                            id = user.id,
                            name = user.name,
                            avatarUrl = user.avatar?.medium,
                            token = token
                        )
                    )
                }
            }
        }
        return coroutineScope {
            val trendingDeferred = async { aniListRepository.browseMedia(sort = listOf("TRENDING_DESC"), page = 1) }
            // One MediaListCollection request covers all five statuses; the
            // per-status path remains as a fallback (and keeps test fakes that
            // only implement getMediaList working).
            val byStatus = aniListRepository.getMediaListCollection(user.id).getOrNull()
                ?: fetchListsIndividually(user.id)
            val current = byStatus[MediaListStatus.CURRENT].orEmpty()
            val planning = byStatus[MediaListStatus.PLANNING].orEmpty()
            val completed = byStatus[MediaListStatus.COMPLETED].orEmpty()
            val dropped = byStatus[MediaListStatus.DROPPED].orEmpty()
            val paused = byStatus[MediaListStatus.PAUSED].orEmpty()
            val userMediaIds = (current + planning + completed + dropped + paused)
                .map { it.media.id }.toSet()
            val recommended = trendingDeferred.await().getOrElse { emptyList() }
                .filter { it.id !in userMediaIds }
                .take(20)
                .map { it.toAnimeEntry() }
            // Adult content hidden from the library/recommendations unless enabled.
            val allowAdult = animeSettingsStore?.settings?.first()?.enableAdultContent ?: true
            val keepAdult: (List<AnimeEntry>) -> List<AnimeEntry> =
                { list -> if (allowAdult) list else list.filterNot { it.isAdult } }
            HomeUiState.Success(
                currentList = keepAdult(current.map { it.toAnimeEntry() }),
                planningList = keepAdult(planning.map { it.toAnimeEntry() }),
                completedList = keepAdult(completed.map { it.toAnimeEntry() }),
                droppedList = keepAdult(dropped.map { it.toAnimeEntry() }),
                pausedList = keepAdult(paused.map { it.toAnimeEntry() }),
                recommendedList = keepAdult(recommended),
                stats = computeLibraryStats(current, planning, completed, dropped, paused)
            )
        }
    }

    private suspend fun fetchListsIndividually(
        userId: Int
    ): Map<MediaListStatus, List<AniListMediaListEntry>> = coroutineScope {
        listOf(
            MediaListStatus.CURRENT,
            MediaListStatus.PLANNING,
            MediaListStatus.COMPLETED,
            MediaListStatus.DROPPED,
            MediaListStatus.PAUSED
        ).map { status ->
            status to async { aniListRepository.getMediaList(userId, status) }
        }.associate { (status, deferred) ->
            status to deferred.await().getOrElse { emptyList() }
        }
    }
}

private fun AniListMediaListEntry.toAnimeEntry() = AnimeEntry(
    id = media.id,
    title = media.title?.english ?: media.title?.romaji ?: "Unknown",
    coverImageUrl = media.coverImage?.extraLarge ?: media.coverImage?.large ?: "",
    bannerImageUrl = media.bannerImage,
    progress = progress,
    totalEpisodes = media.episodes,
    nextAiringEpisode = media.nextAiringEpisode?.episode,
    isAdult = media.isAdult
)

/** Assumed episode length when AniList reports no [AniListMedia.duration]. */
private const val DEFAULT_EPISODE_MINUTES = 24

/**
 * Folds the five status lists into a single [LibraryStats]. Watch time uses each
 * media's own duration, falling back to [DEFAULT_EPISODE_MINUTES]. Scores are
 * normalised to a 0..10 scale (POINT_100 values divided by 10) before bucketing so
 * the distribution and mean are format-agnostic.
 */
private fun computeLibraryStats(
    current: List<AniListMediaListEntry>,
    planning: List<AniListMediaListEntry>,
    completed: List<AniListMediaListEntry>,
    dropped: List<AniListMediaListEntry>,
    paused: List<AniListMediaListEntry>
): LibraryStats {
    val all = current + planning + completed + dropped + paused
    val distribution = IntArray(10)
    var scoreSum = 0.0
    var scoredCount = 0
    var episodes = 0
    var minutes = 0
    val genreCounts = HashMap<String, Int>()
    val formatCounts = HashMap<String, Int>()

    for (entry in all) {
        episodes += entry.progress
        minutes += entry.progress * (entry.media.duration ?: DEFAULT_EPISODE_MINUTES)

        val raw = entry.score ?: 0.0
        if (raw > 0.0) {
            val normalised = if (raw > 10.0) raw / 10.0 else raw
            val bucket = normalised.roundToInt().coerceIn(1, 10)
            distribution[bucket - 1]++
            scoreSum += normalised
            scoredCount++
        }
        for (genre in entry.media.genres) {
            genreCounts[genre] = (genreCounts[genre] ?: 0) + 1
        }
        entry.media.format?.let { formatCounts[it] = (formatCounts[it] ?: 0) + 1 }
    }

    val statusCounts = listOf(
        "Watching" to current.size,
        "Planning" to planning.size,
        "Completed" to completed.size,
        "Dropped" to dropped.size,
        "Paused" to paused.size
    ).filter { it.second > 0 }

    fun Map<String, Int>.rankedDesc() = entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key to it.value }

    return LibraryStats(
        totalAnime = all.size,
        episodesWatched = episodes,
        minutesWatched = minutes,
        meanScore = if (scoredCount > 0) scoreSum / scoredCount else 0.0,
        scoreDistribution = distribution.toList(),
        statusCounts = statusCounts,
        topGenres = genreCounts.rankedDesc().take(6),
        formatCounts = formatCounts.rankedDesc()
    )
}

private fun AniListMedia.toAnimeEntry() = AnimeEntry(
    id = id,
    title = title?.english ?: title?.romaji ?: "Unknown",
    coverImageUrl = coverImage?.extraLarge ?: coverImage?.large ?: "",
    bannerImageUrl = bannerImage,
    progress = 0,
    totalEpisodes = episodes,
    isAdult = isAdult
)
