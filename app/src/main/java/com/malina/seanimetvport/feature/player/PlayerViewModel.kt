package com.malina.seanimetvport.feature.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.malina.seanimetvport.data.anilist.model.MediaListStatus
import com.malina.seanimetvport.data.anilist.repository.AniListRepository
import com.malina.seanimetvport.data.history.WatchHistoryEntry
import com.malina.seanimetvport.data.history.WatchHistoryStore
import com.malina.seanimetvport.data.metadata.AniSkipClient
import com.malina.seanimetvport.data.metadata.SkipInterval
import com.malina.seanimetvport.data.metadata.SkipType
import com.malina.seanimetvport.data.player.SubtitleStyle
import com.malina.seanimetvport.data.player.SubtitleStylePrefs
import com.malina.seanimetvport.data.plugin.model.Episode
import com.malina.seanimetvport.data.plugin.model.EpisodeServer
import com.malina.seanimetvport.data.plugin.model.Subtitle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

sealed class PlayerUiState {
    object Loading : PlayerUiState()
    data class Ready(
        val title: String,
        val episodeNumber: Int,
        val hasNextEpisode: Boolean,
        val subtitleTracks: List<Subtitle>,
        val audioTracks: List<String>
    ) : PlayerUiState()
    data class Error(val message: String) : PlayerUiState()
}

/** Live playback position/duration for the control bar's seek slider. */
data class PlaybackProgress(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L
)

/** A selectable video quality (HLS variant / progressive source), or "Auto". */
data class QualityOption(
    val label: String,
    val isAuto: Boolean,
    val isSelected: Boolean,
    val height: Int = 0
)

/** A selectable subtitle track, including the "Off" option. */
data class SubtitleOption(
    val label: String,
    val isOff: Boolean,
    val isSelected: Boolean,
    val trackIndex: Int = -1
)

/** A selectable audio track (language / dub). No "Off" — one is always active. */
data class AudioOption(
    val label: String,
    val isSelected: Boolean,
    val trackIndex: Int = -1
)

class PlayerViewModel(
    private val aniListRepository: AniListRepository,
    private val okHttpClient: OkHttpClient,
    private val watchHistoryStore: WatchHistoryStore? = null,
    private val subtitleStylePrefs: SubtitleStylePrefs? = null,
    private val aniSkipClient: AniSkipClient? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    // ExoPlayer is created once per initialize call. Exposed as StateFlow so the
    // PlayerScreen's AndroidView reliably re-attaches it the moment it's created
    // — a plain var would not trigger recomposition and the surface would never
    // bind, leaving a decoding-but-black player.
    private val _exoPlayer = MutableStateFlow<ExoPlayer?>(null)
    val exoPlayerFlow: StateFlow<ExoPlayer?> = _exoPlayer.asStateFlow()
    val exoPlayer: ExoPlayer? get() = _exoPlayer.value

    private val _progress = MutableStateFlow(PlaybackProgress())
    val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()

    /** True while ExoPlayer rebuffers mid-playback — drives the spinner overlay. */
    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _qualities = MutableStateFlow<List<QualityOption>>(emptyList())
    val qualities: StateFlow<List<QualityOption>> = _qualities.asStateFlow()

    private val _subtitles = MutableStateFlow<List<SubtitleOption>>(emptyList())
    val subtitles: StateFlow<List<SubtitleOption>> = _subtitles.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<AudioOption>>(emptyList())
    val audioTracks: StateFlow<List<AudioOption>> = _audioTracks.asStateFlow()

    // AniSkip intro/outro intervals for the current episode. activeSkip holds
    // the interval the playhead is currently inside (null otherwise) — the
    // data-class equality means the StateFlow only emits on enter/exit, so the
    // UI pill costs nothing per progress tick.
    private var skipIntervals: List<SkipInterval> = emptyList()
    private var skipTimesLoaded = false
    private var malId: Int = 0
    private val _activeSkip = MutableStateFlow<SkipInterval?>(null)
    val activeSkip: StateFlow<SkipInterval?> = _activeSkip.asStateFlow()

    // True once the playhead enters the final [END_ZONE_MS]. Like activeSkip it
    // only emits on the boolean transition, so the "Next episode" pill costs
    // nothing per progress tick (unlike collecting the 500ms progress flow).
    private val _inEndZone = MutableStateFlow(false)
    val inEndZone: StateFlow<Boolean> = _inEndZone.asStateFlow()

    /** Seeks past the interval the playhead is currently inside (OK on the remote). */
    fun skipCurrentInterval() {
        val interval = _activeSkip.value ?: return
        exoPlayer?.seekTo(interval.endMs)
    }

    /** Fetches skip times once the MAL id AND the episode duration are known. */
    private fun maybeLoadSkipTimes() {
        if (skipTimesLoaded) return
        val client = aniSkipClient ?: return
        val durationMs = exoPlayer?.duration ?: return
        if (malId <= 0 || durationMs <= 0) return
        skipTimesLoaded = true
        viewModelScope.launch {
            skipIntervals = client.getSkipTimes(malId, episodeNumber, durationMs / 1000)
        }
    }

    /** Persisted subtitle appearance/position, applied to the player's SubtitleView. */
    val subtitleStyle: StateFlow<SubtitleStyle> =
        (subtitleStylePrefs?.style ?: flowOf(SubtitleStyle.DEFAULT))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubtitleStyle.DEFAULT)

    private fun updateSubtitleStyle(transform: (SubtitleStyle) -> SubtitleStyle) {
        val prefs = subtitleStylePrefs ?: return
        viewModelScope.launch { prefs.update(transform) }
    }

    fun changeSubtitleSize(deltaSp: Float) = updateSubtitleStyle {
        it.copy(textSizeSp = (it.textSizeSp + deltaSp).coerceIn(SubtitleStyle.MIN_SIZE_SP, SubtitleStyle.MAX_SIZE_SP))
    }
    fun setSubtitleColor(color: Int) = updateSubtitleStyle { it.copy(foregroundColor = color) }
    fun setSubtitleBackground(enabled: Boolean) = updateSubtitleStyle { it.copy(backgroundEnabled = enabled) }
    fun nudgeSubtitle(dxDp: Float, dyDp: Float) = updateSubtitleStyle {
        it.copy(
            offsetXDp = (it.offsetXDp + dxDp).coerceIn(-500f, 500f),
            offsetYDp = (it.offsetYDp + dyDp).coerceIn(-500f, 200f)
        )
    }
    fun centerSubtitle() = updateSubtitleStyle { it.copy(offsetXDp = 0f) }
    fun resetSubtitleStyle() = updateSubtitleStyle { SubtitleStyle.DEFAULT }

    // Emitted when playback reaches the end — the screen advances to the next episode.
    private val _playNextEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val playNextEvent: SharedFlow<Int> = _playNextEvent.asSharedFlow()

    private var progressJob: kotlinx.coroutines.Job? = null
    private var progressSaved = false
    private var mediaId: Int = 0
    private var episodeNumber: Int = 0

    // "Is there a next episode?" state. The player route cannot carry the provider's
    // episode list, so [episodeList] is usually empty and the answer comes from the
    // AniList total fetched below — without it hasNextEpisode was permanently false
    // and the next-episode pill could never appear.
    private var episodeList: List<Episode> = emptyList()
    private var totalEpisodes: Int = 0
    private var currentTitle: String = ""
    private var currentSubtitles: List<Subtitle> = emptyList()

    private fun hasNextEpisode(): Boolean =
        episodeList.any { it.number == episodeNumber + 1 } ||
            (totalEpisodes > 0 && episodeNumber < totalEpisodes)

    /** (Re)publishes the Ready state — also called when the AniList total arrives. */
    private fun publishReadyState() {
        _uiState.value = PlayerUiState.Ready(
            title = currentTitle,
            episodeNumber = episodeNumber,
            hasNextEpisode = hasNextEpisode(),
            subtitleTracks = currentSubtitles,
            audioTracks = emptyList()
        )
    }

    // Local watch-history / resume state (see WatchHistoryStore).
    private var mediaTitle: String = ""
    private var mediaCover: String = ""
    private var mediaBanner: String? = null
    private var resumeMs: Long = 0L
    private var resumeLoaded = false
    private var playerReady = false
    private var hasResumed = false

    fun initialize(
        context: Context,
        episodeServer: EpisodeServer,
        mediaId: Int,
        episodeNumber: Int,
        title: String,
        episodes: List<Episode>
    ) {
        // Release any previously active player before creating a new one.
        _exoPlayer.value?.release()
        _exoPlayer.value = null

        this.mediaId = mediaId
        this.episodeNumber = episodeNumber
        this.mediaTitle = title
        this.episodeList = episodes
        this.currentTitle = title
        this.totalEpisodes = 0
        progressSaved = false
        mediaCover = ""; mediaBanner = null
        resumeMs = 0L; resumeLoaded = false; playerReady = false; hasResumed = false
        skipIntervals = emptyList(); skipTimesLoaded = false; malId = 0
        _activeSkip.value = null
        _inEndZone.value = false
        _isBuffering.value = false

        // Load the saved resume position + series artwork (for the Home history
        // row) off the main path; resume once both the player and this complete.
        viewModelScope.launch {
            resumeMs = watchHistoryStore?.positionFor(mediaId, episodeNumber) ?: 0L
            resumeLoaded = true
            maybeResume()
            aniListRepository.getMediaById(mediaId).onSuccess { m ->
                mediaCover = m.coverImage?.extraLarge ?: m.coverImage?.large ?: mediaCover
                mediaBanner = m.bannerImage ?: mediaBanner
                if (mediaTitle.isBlank()) mediaTitle = m.title?.english ?: m.title?.romaji ?: mediaTitle
                malId = m.idMal ?: 0
                // Total episode count -> whether a next episode exists. Republish the
                // Ready state because this resolves after STATE_READY.
                totalEpisodes = m.episodes ?: 0
                if (_uiState.value is PlayerUiState.Ready) publishReadyState()
                maybeLoadSkipTimes()
            }
        }

        val videoSource = episodeServer.videoSources.firstOrNull()
            ?: run { _uiState.value = PlayerUiState.Error("No video source"); return }

        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient).apply {
            setDefaultRequestProperties(episodeServer.headers)
        }

        // Pick a default subtitle: the provider's flagged default, else the first
        // English track, else the first track — so subtitles are ON by default.
        val defaultSubIndex = videoSource.subtitles.indexOfFirst { it.isDefault }
            .takeIf { it >= 0 }
            ?: videoSource.subtitles.indexOfFirst { it.language.startsWith("en", ignoreCase = true) }
                .takeIf { it >= 0 }
            ?: 0
        val subtitleConfigs = videoSource.subtitles.mapIndexed { index, sub ->
            val mime = subtitleMimeType(sub.url)
            // Diagnostics for the intermittent "no subtitles / wrong timing" reports:
            // the resolved MIME decides which parser runs, so log it alongside the
            // URL it was derived from.
            android.util.Log.i(
                "PlayerVM",
                "subtitle[$index] lang=${sub.language.ifBlank { "und" }} mime=$mime " +
                    "default=${index == defaultSubIndex} url=${sub.url}"
            )
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                .setMimeType(mime)
                .setLanguage(sub.language.ifBlank { "und" })
                .setLabel(friendlyLangLabel(sub.language))
                .setSelectionFlags(if (index == defaultSubIndex) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
        val defaultSubLanguage = videoSource.subtitles.getOrNull(defaultSubIndex)?.language

        val mediaItem = MediaItem.Builder()
            .setUri(videoSource.url)
            .setSubtitleConfigurations(subtitleConfigs)
            .build()

        val mainSource = when (videoSource.type) {
            "m3u8" -> HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            else -> ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        }
        // The per-type factories above IGNORE the MediaItem's sidecar subtitle
        // configurations (only DefaultMediaSourceFactory merges them). So build
        // and merge the external subtitle sources explicitly — otherwise the
        // provider's .vtt tracks never appear and only embedded CEA-608 shows.
        val mediaSource = if (subtitleConfigs.isEmpty()) {
            mainSource
        } else {
            val subtitleSources = subtitleConfigs.map { config ->
                androidx.media3.exoplayer.source.SingleSampleMediaSource
                    .Factory(dataSourceFactory)
                    // A subtitle track that fails to download must never stall the
                    // episode: treat the failure as end-of-stream so the video plays
                    // on (subtitle-less) instead of the player retrying into a hang.
                    .setTreatLoadErrorsAsEndOfStream(true)
                    .createMediaSource(config, C.TIME_UNSET)
            }
            // adjustPeriodTimeOffsets = true. Sideloaded subtitle files are always
            // timed from zero, but an HLS timeline commonly starts at an arbitrary
            // MPEG-TS PTS offset. Merging without this renders every cue shifted by
            // that offset — the "subtitles wildly out of sync" report.
            androidx.media3.exoplayer.source.MergingMediaSource(
                /* adjustPeriodTimeOffsets = */ true,
                mainSource, *subtitleSources.toTypedArray()
            )
        }

        // Enable decoder fallback: if the hardware decoder fails to initialize
        // or render (some SoCs / firmware), ExoPlayer retries with a software
        // decoder instead of showing a black frame.
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
        // ExoPlayer's default LoadControl buffers up to ~50s of media — too
        // much on a 1.4GB TV box streaming HLS next to a decoding pipeline.
        // 15–30s forward buffer + a 10s back-buffer keeps memory flat without
        // hurting seek-back UX.
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 30_000, 1_500, 3_000)
            .setBackBuffer(10_000, false)
            .build()
        val player = ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .build()
        // Subtitles ON by default: prefer the chosen language (or any English /
        // undetermined track for HLS-embedded subs) and never start with text
        // disabled. The user can turn them off via the CC control.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredTextLanguage(defaultSubLanguage ?: "en")
            .setSelectUndeterminedTextLanguage(true)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onLoadError(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
                mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData,
                error: java.io.IOException,
                wasCanceled: Boolean
            ) {
                val kind = if (mediaLoadData.trackType == C.TRACK_TYPE_TEXT) {
                    "TEXT"
                } else {
                    "trackType=${mediaLoadData.trackType}"
                }
                android.util.Log.w(
                    "PlayerVM",
                    "load error ($kind): ${error.javaClass.simpleName}: ${error.message} " +
                        "uri=${loadEventInfo.uri} canceled=$wasCanceled"
                )
            }
        })
        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.w("PlayerVM", "playback error: ${error.errorCodeName}: ${error.message}")
                _uiState.value = PlayerUiState.Error(error.message ?: "Playback error")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    currentSubtitles = videoSource.subtitles
                    publishReadyState()
                    playerReady = true
                    maybeResume()
                    maybeLoadSkipTimes()
                }
                if (playbackState == Player.STATE_ENDED) {
                    // Episode finished — advance to the next one in the series.
                    _playNextEvent.tryEmit(episodeNumber + 1)
                }
                checkProgressInternal(player.currentPosition, player.duration)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                checkProgressInternal(player.currentPosition, player.duration)
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                refreshTracks(player)
            }


            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _progress.value = _progress.value.copy(isPlaying = isPlaying)
                // Pause is a natural checkpoint — capture the resume position
                // now that periodic writes only happen every 60s.
                if (!isPlaying) recordHistory()
            }
        })

        _exoPlayer.value = player
        startProgressLoop()
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            var tick = 0
            while (true) {
                exoPlayer?.let { p ->
                    val position = p.currentPosition.coerceAtLeast(0)
                    _progress.value = PlaybackProgress(
                        isPlaying = p.isPlaying,
                        positionMs = position,
                        durationMs = p.duration.coerceAtLeast(0),
                        bufferedMs = p.bufferedPosition.coerceAtLeast(0)
                    )
                    val durationMs = p.duration.coerceAtLeast(0)
                    _activeSkip.value = skipIntervals.firstOrNull { interval ->
                        position >= interval.startMs && position < interval.endMs &&
                            // AniSkip occasionally returns a bogus outro interval in the
                            // middle of the episode. Only surface an OUTRO once the playhead
                            // is genuinely in the final stretch (interval ends within the
                            // last 15% of the runtime).
                            (interval.type != SkipType.OUTRO || durationMs <= 0 ||
                                interval.endMs >= durationMs * 0.85)
                    }
                    _inEndZone.value = isInEndZone(position, durationMs)
                }
                tick++
                // AniList 80% progress check every 5s — the player emits no
                // position events during uninterrupted playback, so without
                // this the save only fired at STATE_ENDED (exiting at 85%
                // would lose the progress).
                if (tick % 10 == 0) {
                    exoPlayer?.let { checkProgressInternal(it.currentPosition, it.duration) }
                }
                // Persist the resume position every 60s. Writing DataStore is a
                // full JSON re-encode + fsync on slow eMMC — too heavy to run
                // every 5s alongside video decoding. Pause and close() also
                // write, so at most ~60s of resume position can be lost.
                if (tick % 120 == 0) recordHistory()
                kotlinx.coroutines.delay(500)
            }
        }
    }

    /** Seeks to the saved resume position once both the player and it are ready. */
    private fun maybeResume() {
        if (hasResumed || !playerReady || !resumeLoaded) return
        hasResumed = true
        val p = exoPlayer ?: return
        val dur = p.duration
        // Only resume for a meaningful offset that isn't essentially "finished".
        if (resumeMs > 5_000 && (dur <= 0 || resumeMs < dur * 0.9)) p.seekTo(resumeMs)
    }

    /** Writes the current series/episode/position to local watch history. */
    private fun recordHistory() {
        val store = watchHistoryStore ?: return
        val p = exoPlayer ?: return
        val pos = p.currentPosition.coerceAtLeast(0)
        val dur = p.duration.coerceAtLeast(0)
        if (mediaId <= 0 || dur <= 0 || pos <= 0) return
        // Watched into the final couple of minutes → treat the episode as finished:
        // drop it from the resume ("continue watching") list instead of recording a
        // near-the-end resume point the user would just skip past.
        if (isInEndZone(pos, dur)) {
            viewModelScope.launch { store.remove(mediaId) }
            return
        }
        viewModelScope.launch {
            store.record(
                WatchHistoryEntry(
                    anilistId = mediaId,
                    title = mediaTitle,
                    coverImageUrl = mediaCover,
                    bannerImageUrl = mediaBanner,
                    episodeNumber = episodeNumber,
                    positionMs = pos,
                    durationMs = dur,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** Enumerates the current video-quality variants and subtitle tracks for the UI. */
    private fun refreshTracks(player: ExoPlayer) {
        val tracks = player.currentTracks

        // ── Quality (video variants) ─────────────────────────────────────
        val qualityOptions = mutableListOf<QualityOption>()
        val params = player.trackSelectionParameters
        val hasVideoOverride = params.overrides.keys.any { group ->
            group.type == C.TRACK_TYPE_VIDEO
        }
        // When Auto is active, surface the resolution ExoPlayer actually settled
        // on (e.g. "Auto (480p)") so the label reflects the current stream.
        val activeVideoHeight = tracks.groups
            .filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { group ->
                (0 until group.length)
                    .filter { group.isTrackSelected(it) }
                    .map { group.getTrackFormat(it).height }
            }
            .firstOrNull { it > 0 } ?: 0
        val autoLabel = if (!hasVideoOverride && activeVideoHeight > 0) {
            "Auto (${activeVideoHeight}p)"
        } else {
            "Auto"
        }
        qualityOptions.add(QualityOption(autoLabel, isAuto = true, isSelected = !hasVideoOverride))
        tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.forEach { group ->
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val h = format.height
                if (h > 0) {
                    val selected = group.isTrackSelected(i) && hasVideoOverride
                    qualityOptions.add(
                        QualityOption("${h}p", isAuto = false, isSelected = selected, height = h)
                    )
                }
            }
        }
        // De-duplicate by height, keep highest first
        _qualities.value = qualityOptions
            .distinctBy { if (it.isAuto) -1 else it.height }
            .sortedByDescending { if (it.isAuto) Int.MAX_VALUE else it.height }

        // ── Subtitles ────────────────────────────────────────────────────
        val subOptions = mutableListOf<SubtitleOption>()
        val textDisabled = params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val anyTextSelected = tracks.groups.any {
            it.type == C.TRACK_TYPE_TEXT && it.isSelected
        }
        subOptions.add(SubtitleOption("Off", isOff = true, isSelected = textDisabled || !anyTextSelected))
        var idx = 0
        tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.forEach { group ->
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val lang = format.language ?: "und"
                val label = format.label?.takeIf { it.isNotBlank() && !it.equals("und", true) }
                    ?: friendlyLangLabel(lang)
                subOptions.add(
                    SubtitleOption(
                        label = label,
                        isOff = false,
                        isSelected = !textDisabled && group.isTrackSelected(i),
                        trackIndex = idx
                    )
                )
                idx++
            }
        }
        _subtitles.value = subOptions

        // ── Audio (language / dub) ───────────────────────────────────────
        val audioOptions = mutableListOf<AudioOption>()
        var aIdx = 0
        tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEach { group ->
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val lang = format.language ?: "und"
                val label = format.label?.takeIf { it.isNotBlank() && !it.equals("und", true) }
                    ?: if (lang.isBlank() || lang.equals("und", true)) "Track ${aIdx + 1}"
                    else friendlyLangLabel(lang)
                audioOptions.add(
                    AudioOption(
                        label = label,
                        isSelected = group.isTrackSelected(i),
                        trackIndex = aIdx
                    )
                )
                aIdx++
            }
        }
        _audioTracks.value = audioOptions
    }

    fun selectQuality(option: QualityOption) {
        val player = exoPlayer ?: return
        val builder = player.trackSelectionParameters.buildUpon()
        if (option.isAuto) {
            builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        } else {
            val group = player.currentTracks.groups
                .firstOrNull { it.type == C.TRACK_TYPE_VIDEO } ?: return
            val trackIndex = (0 until group.length).firstOrNull {
                group.getTrackFormat(it).height == option.height
            } ?: return
            builder.setOverrideForType(
                androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
            )
        }
        player.trackSelectionParameters = builder.build()
        refreshTracks(player)
    }

    fun selectSubtitle(option: SubtitleOption) {
        val player = exoPlayer ?: return
        val builder = player.trackSelectionParameters.buildUpon()
        if (option.isOff) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            val textGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            var running = 0
            outer@ for (group in textGroups) {
                for (i in 0 until group.length) {
                    if (running == option.trackIndex) {
                        builder.setOverrideForType(
                            androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, i)
                        )
                        break@outer
                    }
                    running++
                }
            }
        }
        player.trackSelectionParameters = builder.build()
        refreshTracks(player)
    }

    fun selectAudio(option: AudioOption) {
        val player = exoPlayer ?: return
        val builder = player.trackSelectionParameters.buildUpon()
        builder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
        val audioGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        var running = 0
        outer@ for (group in audioGroups) {
            for (i in 0 until group.length) {
                if (running == option.trackIndex) {
                    builder.setOverrideForType(
                        androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, i)
                    )
                    break@outer
                }
                running++
            }
        }
        player.trackSelectionParameters = builder.build()
        refreshTracks(player)
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs.coerceAtLeast(0))
    }

    /**
     * Checks whether playback has reached the 80 % threshold and saves progress once.
     * Exposed as internal so [PlayerViewModelTest] can call it directly without ExoPlayer.
     */
    internal fun checkProgressInternal(position: Long, duration: Long) {
        if (progressSaved) return
        // The 80% rule alone decides "watched". An extra absolute end-zone clause
        // used to sit here, but it was redundant for normal episodes (the last two
        // minutes of a 24-minute episode is already 92%) and actively wrong for
        // short ones: for anything under two minutes it was true at position 0, so
        // STATE_READY marked the episode watched before a frame played.
        if (isAt80Percent(position, duration)) {
            progressSaved = true
            viewModelScope.launch {
                aniListRepository.saveProgress(
                    mediaId = mediaId,
                    progress = episodeNumber,
                    status = MediaListStatus.CURRENT
                )
            }
        }
    }

    /**
     * Sets up the playback context fields used by [checkProgressInternal] without creating an
     * ExoPlayer. Call this in tests before [checkProgressInternal] instead of [initialize].
     */
    internal fun initTestState(mediaId: Int, episodeNumber: Int) {
        this.mediaId = mediaId
        this.episodeNumber = episodeNumber
        progressSaved = false
    }

    /**
     * Transitions the view-model to [PlayerUiState.Error].
     * Exposed as internal for tests that verify error-state handling without ExoPlayer.
     */
    internal fun simulateError(message: String) {
        _uiState.value = PlayerUiState.Error(message)
    }

    fun seekBy(deltaMs: Long) {
        exoPlayer?.seekTo((exoPlayer!!.currentPosition + deltaMs).coerceAtLeast(0))
    }

    fun togglePlayPause() {
        exoPlayer?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    companion object {
        /**
         * Trailing window (ms) treated as "finished": inside it the episode counts
         * as watched, drops from the resume list, and the screen offers a manual
         * "Next episode" jump. 2 minutes.
         */
        const val END_ZONE_MS = 120_000L
    }

    fun close() {
        recordHistory() // capture the final resume position before releasing
        // Also run the 80% AniList check at the exit position — covers exiting
        // between the last 5s tick and the end of the episode.
        _exoPlayer.value?.let { checkProgressInternal(it.currentPosition, it.duration) }
        progressJob?.cancel()
        progressJob = null
        _exoPlayer.value?.release()
        _exoPlayer.value = null
    }
}

/**
 * Resolves a subtitle track's MIME type from its URL.
 *
 * Matches on the URL **path** only. Provider/CDN subtitle links routinely carry a
 * query string (".../sub.srt?token=…&expires=…"); matching the whole URL made every
 * such track miss its extension and fall through to WebVTT, whose parser then
 * rejects the SubRip/SSA payload outright — so the track silently produced no cues
 * at all. WebVTT stays the fallback for extension-less URLs, which is what most
 * providers actually serve.
 */
internal fun subtitleMimeType(url: String): String {
    // Plain string trimming rather than Uri.parse: this must stay a pure JVM
    // function so it is unit-testable (android.net.Uri is stubbed in unit tests).
    val path = url.substringBefore('#').substringBefore('?').lowercase()
    return when {
        path.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
        path.endsWith(".ass") || path.endsWith(".ssa") -> MimeTypes.TEXT_SSA
        else -> MimeTypes.TEXT_VTT
    }
}

/**
 * Maps a language code to a human label. Blank / "und" (undetermined — the ISO
 * code providers send when they don't tag the track) becomes "Subtitles" so the
 * CC control never shows a raw code.
 */
private fun friendlyLangLabel(code: String?): String {
    val c = code?.trim().orEmpty()
    if (c.isBlank() || c.equals("und", ignoreCase = true)) return "Subtitles"
    return when (c.lowercase().take(2)) {
        "en" -> "English"
        "ja" -> "Japanese"
        "es" -> "Spanish"
        "pt" -> "Portuguese"
        "fr" -> "French"
        "de" -> "German"
        "it" -> "Italian"
        "ar" -> "Arabic"
        "pl" -> "Polish"
        "ru" -> "Russian"
        "zh" -> "Chinese"
        "ko" -> "Korean"
        else -> c
    }
}

/**
 * Pure function: returns true when [position] is at or above 80 % of [duration].
 * Extracted from the ViewModel so it can be tested without Android dependencies.
 */
internal fun isAt80Percent(position: Long, duration: Long): Boolean =
    duration > 0 && position.toFloat() / duration >= 0.8f

/**
 * True when [position] sits in the trailing "practically finished" window of
 * [duration] — the window that drops the episode from the resume list and offers
 * the manual "Next episode" jump.
 *
 * The window is [PlayerViewModel.END_ZONE_MS], but never more than 20 % of the
 * runtime. Without that cap a sub-two-minute episode satisfies the condition at
 * position 0, so it would vanish from "Recently watched" and show the next-episode
 * pill before playing at all.
 */
internal fun isInEndZone(position: Long, duration: Long): Boolean {
    if (duration <= 0) return false
    val window = minOf(PlayerViewModel.END_ZONE_MS, duration / 5)
    return duration - position <= window
}
