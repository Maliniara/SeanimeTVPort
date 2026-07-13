package com.example.seanimetv.feature.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.seanimetv.data.anilist.model.AniListFuzzyDate
import com.example.seanimetv.data.anilist.model.AniListMedia
import com.example.seanimetv.data.anilist.model.MediaListStatus
import com.example.seanimetv.data.anilist.repository.AniListRepository
import com.example.seanimetv.data.plugin.PluginEngine
import com.example.seanimetv.data.plugin.PluginRepository
import com.example.seanimetv.data.plugin.model.Episode
import com.example.seanimetv.data.plugin.model.PluginEntry
import com.example.seanimetv.data.plugin.model.EpisodeServer
import com.example.seanimetv.data.net.toUserMessage
import com.example.seanimetv.data.plugin.model.SearchResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SeriesDetailUiState {
    object Loading : SeriesDetailUiState()
    data class Success(
        val media: AniListMedia,
        val episodes: List<Episode>,
        val availableServers: List<String>
    ) : SeriesDetailUiState()
    data class Error(val message: String) : SeriesDetailUiState()
}

class SeriesDetailViewModel(
    private val aniListRepository: AniListRepository,
    private val engine: PluginEngine,
    private val pluginRepository: PluginRepository? = null,
    private val aniZipClient: com.example.seanimetv.data.metadata.AniZipClient? = null
) : ViewModel() {

    private val _navigationEvent = MutableSharedFlow<PlaybackRequest>(extraBufferCapacity = 1)
    val navigationEvent: SharedFlow<PlaybackRequest> = _navigationEvent.asSharedFlow()

    private val _uiState = MutableStateFlow<SeriesDetailUiState>(SeriesDetailUiState.Loading)
    val uiState: StateFlow<SeriesDetailUiState> = _uiState.asStateFlow()

    private val _selectedServer = MutableStateFlow("")
    val selectedServer: StateFlow<String> = _selectedServer.asStateFlow()

    private val _isLoadingServer = MutableStateFlow(false)
    val isLoadingServer: StateFlow<Boolean> = _isLoadingServer.asStateFlow()

    private val _trailerVisible = MutableStateFlow(false)
    val trailerVisible: StateFlow<Boolean> = _trailerVisible.asStateFlow()

    fun showTrailer() { _trailerVisible.value = true }
    fun hideTrailer() { _trailerVisible.value = false }

    private val _isDub = MutableStateFlow(false)
    val isDub: StateFlow<Boolean> = _isDub.asStateFlow()

    /** True while re-resolving episodes after a provider/dub change (small inline spinner). */
    private val _isReloading = MutableStateFlow(false)
    val isReloading: StateFlow<Boolean> = _isReloading.asStateFlow()

    fun toggleDub() {
        _isDub.value = !_isDub.value
        // Dub availability differs per provider — re-resolve episodes in place.
        reloadEpisodes()
    }

    // ── Provider switching (Watch panel) ─────────────────────────────────
    private val _providers = MutableStateFlow<List<PluginEntry>>(emptyList())
    val providers: StateFlow<List<PluginEntry>> = _providers.asStateFlow()

    private val _selectedProviderId = MutableStateFlow<String?>(null)
    val selectedProviderId: StateFlow<String?> = _selectedProviderId.asStateFlow()

    // When the "Auto" provider is active, this holds the id of the real provider that
    // actually resolved episodes (for the chip's "Auto · <name>" label); null while
    // resolving or when nothing resolved.
    private val _autoResolvedProviderId = MutableStateFlow<String?>(null)
    val autoResolvedProviderId: StateFlow<String?> = _autoResolvedProviderId.asStateFlow()

    private val _selectedEpisode = MutableStateFlow<Episode?>(null)
    val selectedEpisode: StateFlow<Episode?> = _selectedEpisode.asStateFlow()

    private var lastAnilistId: Int? = null
    /** Provider id currently loaded into the engine (avoids redundant reloads). */
    private var loadedProviderId: String? = null

    // When set (via setResumeTarget), loadMedia auto-plays this episode as soon as
    // it resolves — used by the Home "Recently watched" row to resume directly.
    private var resumeEpisodeNumber: Int? = null
    private val _isResuming = MutableStateFlow(false)
    /** True from the moment a resume is requested until playback launches or fails. */
    val isResuming: StateFlow<Boolean> = _isResuming.asStateFlow()

    fun setResumeTarget(episodeNumber: Int?) {
        resumeEpisodeNumber = episodeNumber?.takeIf { it >= 0 }
        _isResuming.value = resumeEpisodeNumber != null
    }

    fun selectEpisode(episode: Episode) { _selectedEpisode.value = episode }

    /**
     * Switches the active onlinestream provider and reloads episodes. The special
     * [AUTO_PROVIDER_ID] selects "Auto" mode, which lets [resolveEpisodes] iterate
     * every installed provider (active-first) until one returns episodes — the
     * provider-level analogue of the per-episode server fallback in Seanime.
     */
    fun onProviderSelected(id: String) {
        if (id == _selectedProviderId.value) return
        if (id == AUTO_PROVIDER_ID) {
            _selectedProviderId.value = AUTO_PROVIDER_ID
            _autoResolvedProviderId.value = null
            reloadEpisodes()
            return
        }
        val repo = pluginRepository ?: return
        viewModelScope.launch {
            val entry = repo.getInstalledPlugins().find { it.id == id } ?: return@launch
            _isLoadingServer.value = true
            repo.setActivePlugin(id)
            runCatching { engine.loadPlugin(entry) }
            loadedProviderId = id
            _selectedProviderId.value = id
            _autoResolvedProviderId.value = null
            _selectedServer.value = engine.getAvailableServers().firstOrNull() ?: ""
            _isLoadingServer.value = false
            reloadEpisodes()
        }
    }

    /**
     * Re-resolves the episode list for the current media after a provider or dub
     * change, keeping the panel on screen (no full Loading state) and surfacing
     * progress via [isReloading] — a small inline spinner instead of a rebuild.
     */
    private fun reloadEpisodes() {
        val media = (_uiState.value as? SeriesDetailUiState.Success)?.media
            ?: return lastAnilistId?.let { loadMedia(it) } ?: Unit
        viewModelScope.launch {
            _isReloading.value = true
            val (episodes, servers) = resolveEpisodes(media)
            if (servers.isNotEmpty() && _selectedServer.value !in servers) {
                _selectedServer.value = servers.first()
            }
            val progress = media.mediaListEntry?.progress ?: 0
            _selectedEpisode.value =
                episodes.firstOrNull { it.number == progress + 1 } ?: episodes.firstOrNull()
            _uiState.value = SeriesDetailUiState.Success(media, episodes, servers)
            _isReloading.value = false
        }
    }

    /**
     * Resolves the episode list for [media]. In [AUTO_PROVIDER_ID] mode this tries
     * each installed provider (active-first) until one yields episodes; otherwise it
     * uses the single selected provider. Returns episodes + the resolving provider's
     * available servers.
     */
    private suspend fun resolveEpisodes(media: AniListMedia): Pair<List<Episode>, List<String>> {
        if (_selectedProviderId.value == AUTO_PROVIDER_ID) return resolveEpisodesAuto(media)
        ensureProviderLoaded(_selectedProviderId.value)
        val raw = fetchRawEpisodes(media)
        return enrichEpisodes(media, raw) to engine.getAvailableServers()
    }

    /**
     * "Try all available providers" (mirrors Seanime Denshi): load each installed
     * provider in turn and return the first that resolves episodes, recording which
     * one won in [autoResolvedProviderId]. Active provider is tried first so the
     * common case is no slower than a manual selection.
     */
    private suspend fun resolveEpisodesAuto(media: AniListMedia): Pair<List<Episode>, List<String>> {
        val ordered = orderedAutoProviders()
        if (ordered.isEmpty()) {
            // No repo / no installed providers: fall back to whatever the engine holds.
            val raw = fetchRawEpisodes(media)
            _autoResolvedProviderId.value = null
            return enrichEpisodes(media, raw) to engine.getAvailableServers()
        }
        for (entry in ordered) {
            runCatching { engine.loadPlugin(entry) }
            loadedProviderId = entry.id
            val raw = fetchRawEpisodes(media)
            if (raw.isNotEmpty()) {
                _autoResolvedProviderId.value = entry.id
                return enrichEpisodes(media, raw) to engine.getAvailableServers()
            }
        }
        _autoResolvedProviderId.value = null
        return emptyList<Episode>() to engine.getAvailableServers()
    }

    /** Installed onlinestream providers with the persisted active one first. */
    private suspend fun orderedAutoProviders(): List<PluginEntry> {
        val repo = pluginRepository ?: return emptyList()
        val all = _providers.value.ifEmpty {
            repo.getInstalledPlugins().filter {
                (it.typeStr == "onlinestream-provider" || it.assetPath != null) && it.enabled
            }
        }
        val activeId = repo.getActivePlugin()?.id
        return all.sortedByDescending { it.id == activeId }
    }

    /** Ensures [id] is the provider currently loaded in the engine (no-op if already, null, or Auto). */
    private suspend fun ensureProviderLoaded(id: String?) {
        if (id == null || id == AUTO_PROVIDER_ID || id == loadedProviderId) return
        val repo = pluginRepository ?: return
        val entry = repo.getInstalledPlugins().find { it.id == id } ?: return
        runCatching { engine.loadPlugin(entry) }
        loadedProviderId = id
    }

    /** Provider search (best-title match) + raw episode fetch for the loaded provider. */
    private suspend fun fetchRawEpisodes(media: AniListMedia): List<Episode> {
        val title = media.title?.romaji ?: media.title?.english ?: "Unknown"
        val searchId = engine.search(query = title, media = media, dub = _isDub.value)
            .getOrElse { emptyList() }
            .let { pickBestResult(it, media) }?.id
        return if (searchId != null) {
            engine.findEpisodes(searchId).getOrElse { emptyList() }
        } else emptyList()
    }

    /** Per-episode metadata (distinct thumbnail, title, synopsis) from ani.zip — the
     *  same source Seanime uses. Falls back to AniList streamingEpisodes, then the
     *  banner/cover, so tiles and captions are never blank. */
    private suspend fun enrichEpisodes(media: AniListMedia, rawEpisodes: List<Episode>): List<Episode> {
        val aniZip = aniZipClient?.getEpisodes(media.id).orEmpty()
        val fallbackImage = media.bannerImage
            ?: media.coverImage?.extraLarge ?: media.coverImage?.large
        return rawEpisodes.map { ep ->
            val meta = aniZip[ep.number]
            val stream = media.streamingEpisodes.getOrNull(ep.number - 1)
            ep.copy(
                title = ep.title?.takeIf { it.isNotBlank() }
                    ?: meta?.title?.takeIf { it.isNotBlank() }
                    ?: stream?.title,
                thumbnail = meta?.image?.takeIf { it.isNotBlank() }
                    ?: ep.thumbnail?.takeIf { it.isNotBlank() }
                    ?: stream?.thumbnail?.takeIf { it.isNotBlank() }
                    ?: fallbackImage,
                description = meta?.overview?.takeIf { it.isNotBlank() }
            )
        }
    }

    private suspend fun refreshProviders() {
        val repo = pluginRepository ?: return
        _providers.value = repo.getInstalledPlugins().filter {
            (it.typeStr == "onlinestream-provider" || it.assetPath != null) && it.enabled
        }
        // Default to "Auto" (try all providers) on a fresh screen; a manual pick sticks.
        if (_selectedProviderId.value == null) {
            _selectedProviderId.value = AUTO_PROVIDER_ID
        }
    }

    private val _editVisible = MutableStateFlow(false)
    val editVisible: StateFlow<Boolean> = _editVisible.asStateFlow()

    private val _isSavingEntry = MutableStateFlow(false)
    val isSavingEntry: StateFlow<Boolean> = _isSavingEntry.asStateFlow()

    // Transient "Saved / Removed" confirmation shown as a small top-right pill
    // (Denshi-style) instead of a full-screen reload spinner. Null = hidden.
    private val _savedNotice = MutableStateFlow<String?>(null)
    val savedNotice: StateFlow<String?> = _savedNotice.asStateFlow()

    private fun showSavedNotice(message: String) {
        viewModelScope.launch {
            _savedNotice.value = message
            kotlinx.coroutines.delay(2500)
            _savedNotice.value = null
        }
    }

    /**
     * Re-fetches the media (so the edited progress/status/score reflect) and swaps
     * it into the existing Success state — NO [SeriesDetailUiState.Loading], so the
     * card never flashes a full-screen spinner after an edit. Episodes/servers are
     * kept as-is (an entry edit doesn't change them).
     */
    private fun refreshEntryQuietly(mediaId: Int) {
        viewModelScope.launch {
            aniListRepository.getMediaById(mediaId).onSuccess { media ->
                val current = _uiState.value as? SeriesDetailUiState.Success ?: return@onSuccess
                _uiState.value = current.copy(media = media)
            }
        }
    }

    fun showEditDialog() { _editVisible.value = true }
    fun hideEditDialog() { _editVisible.value = false }

    fun saveEntry(
        status: MediaListStatus,
        score: Double,
        progress: Int,
        repeat: Int = 0,
        startedAt: AniListFuzzyDate? = null,
        completedAt: AniListFuzzyDate? = null
    ) {
        val media = (_uiState.value as? SeriesDetailUiState.Success)?.media ?: return
        viewModelScope.launch {
            // Close the dialog immediately and confirm via a small corner pill —
            // no full-screen reload spinner (Denshi-style).
            _editVisible.value = false
            _isSavingEntry.value = true
            aniListRepository.saveMediaListEntry(media.id, status, score, progress, repeat, startedAt, completedAt)
                .onSuccess {
                    refreshEntryQuietly(media.id)
                    _isSavingEntry.value = false
                    showSavedNotice("Saved")
                }
                .onFailure {
                    _isSavingEntry.value = false
                    _uiState.value = SeriesDetailUiState.Error(it.message ?: "Save failed")
                }
        }
    }

    fun deleteEntry() {
        val media = (_uiState.value as? SeriesDetailUiState.Success)?.media ?: return
        val entryId = media.mediaListEntry?.id ?: return
        viewModelScope.launch {
            _editVisible.value = false
            _isSavingEntry.value = true
            aniListRepository.deleteMediaListEntry(entryId, media.id)
                .onSuccess {
                    refreshEntryQuietly(media.id)
                    _isSavingEntry.value = false
                    showSavedNotice("Removed")
                }
                .onFailure {
                    _isSavingEntry.value = false
                    _uiState.value = SeriesDetailUiState.Error(it.message ?: "Delete failed")
                }
        }
    }

    fun loadMedia(anilistId: Int) {
        lastAnilistId = anilistId
        viewModelScope.launch {
            _uiState.value = SeriesDetailUiState.Loading
            refreshProviders()
            // resolveEpisodes() below loads the provider(s) into the engine itself —
            // Auto iterates all of them, manual loads the single selection — so no
            // pre-load step is needed before the search.
            val mediaResult = aniListRepository.getMediaById(anilistId)
            mediaResult.fold(
                onSuccess = { media ->
                    val (episodes, servers) = resolveEpisodes(media)
                    if (servers.isNotEmpty() && _selectedServer.value !in servers) {
                        _selectedServer.value = servers.first()
                    }
                    // Default panel selection: next unwatched episode, else first
                    val progress = media.mediaListEntry?.progress ?: 0
                    _selectedEpisode.value =
                        episodes.firstOrNull { it.number == progress + 1 } ?: episodes.firstOrNull()
                    _uiState.value = SeriesDetailUiState.Success(media, episodes, servers)

                    // Direct resume: auto-play the requested episode once resolved.
                    resumeEpisodeNumber?.let { target ->
                        resumeEpisodeNumber = null
                        val ep = episodes.firstOrNull { it.number == target }
                        if (ep != null) {
                            _selectedEpisode.value = ep
                            onEpisodeSelected(ep)
                        } else {
                            _isResuming.value = false
                        }
                    }
                },
                onFailure = {
                    _isResuming.value = false
                    _uiState.value = SeriesDetailUiState.Error(it.toUserMessage())
                }
            )
        }
    }

    fun onServerSelected(server: String) {
        _selectedServer.value = server
    }

    fun onEpisodeSelected(episode: Episode) {
        viewModelScope.launch {
            _isLoadingServer.value = true
            // Selected server first, then every remaining server — one dead
            // server must not end in an error screen when a sibling can play
            // the episode (mirrors Seanime's automatic server fallback).
            val servers = listOf(_selectedServer.value) +
                engine.getAvailableServers().filter { it != _selectedServer.value }
            var lastError: Throwable? = null
            for (serverName in servers) {
                val result = engine.findEpisodeServer(episode, serverName)
                val server = result.getOrNull()
                if (server != null) {
                    if (serverName != _selectedServer.value) _selectedServer.value = serverName
                    _isLoadingServer.value = false
                    _isResuming.value = false
                    val media = (_uiState.value as? SeriesDetailUiState.Success)?.media
                    // emit (suspending) rather than tryEmit: the auto-resume path
                    // fires this right after the screen composes, and tryEmit would
                    // DROP it if the navigation collector hasn't subscribed yet.
                    _navigationEvent.emit(
                        PlaybackRequest(
                            server = server,
                            mediaId = media?.id ?: 0,
                            episodeNumber = episode.number,
                            title = media?.title?.english ?: media?.title?.romaji ?: "Episode",
                            episodeTitle = episode.title ?: ""
                        )
                    )
                    return@launch
                }
                lastError = result.exceptionOrNull()
            }
            _isLoadingServer.value = false
            _isResuming.value = false
            _uiState.value = SeriesDetailUiState.Error(lastError?.toUserMessage() ?: "Server error")
        }
    }

    /**
     * Closest-title match over romaji/english (case-insensitive Levenshtein) —
     * mirrors Seanime's GetBestSearchResult instead of blindly taking the
     * provider's first hit, which often picks a sequel/special.
     */
    private fun pickBestResult(
        results: List<SearchResult>,
        media: AniListMedia
    ): SearchResult? {
        if (results.size <= 1) return results.firstOrNull()
        val targets = listOfNotNull(media.title?.romaji, media.title?.english)
            .map { it.lowercase() }
        if (targets.isEmpty()) return results.first()
        return results.minByOrNull { result ->
            targets.minOf { levenshtein(result.title.lowercase(), it) }
        }
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }

    companion object {
        /** Sentinel provider id for "Auto" mode (try all installed providers). */
        const val AUTO_PROVIDER_ID = "__auto__"
    }
}

/** Everything the player screen needs, carried from the episode list to the nav layer. */
data class PlaybackRequest(
    val server: EpisodeServer,
    val mediaId: Int,
    val episodeNumber: Int,
    val title: String,
    val episodeTitle: String
)
