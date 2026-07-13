package com.example.seanimetv.feature.player

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
import com.example.seanimetv.data.anilist.model.MediaListStatus
import com.example.seanimetv.data.anilist.repository.AniListRepository
import com.example.seanimetv.data.history.WatchHistoryEntry
import com.example.seanimetv.data.history.WatchHistoryStore
import com.example.seanimetv.data.metadata.AniSkipClient
import com.example.seanimetv.data.metadata.SkipInterval
import com.example.seanimetv.data.player.SubtitleStyle
import com.example.seanimetv.data.player.SubtitleStylePrefs
import com.example.seanimetv.data.plugin.model.Episode
import com.example.seanimetv.data.plugin.model.EpisodeServer
import com.example.seanimetv.data.plugin.model.Subtitle
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
        progressSaved = false
        mediaCover = ""; mediaBanner = null
        resumeMs = 0L; resumeLoaded = false; playerReady = false; hasResumed = false
        skipIntervals = emptyList(); skipTimesLoaded = false; malId = 0
        _activeSkip.value = null
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
            val mime = when {
                sub.url.endsWith(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
                sub.url.endsWith(".ass", ignoreCase = true) ||
                    sub.url.endsWith(".ssa", ignoreCase = true) -> MimeTypes.TEXT_SSA
                else -> MimeTypes.TEXT_VTT
            }
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
                    .createMediaSource(config, C.TIME_UNSET)
            }
            androidx.media3.exoplayer.source.MergingMediaSource(
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
                android.util.Log.w("PlayerVM", "load error (${mediaLoadData.trackType}): ${error.javaClass.simpleName}")
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
                    val hasNext = episodes.any { it.number == episodeNumber + 1 }
                    _uiState.value = PlayerUiState.Ready(
                        title = title,
                        episodeNumber = episodeNumber,
                        hasNextEpisode = hasNext,
                        subtitleTracks = videoSource.subtitles,
                        audioTracks = emptyList()
                    )
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
                    _activeSkip.value = skipIntervals.firstOrNull { interval ->
                        position >= interval.startMs && position < interval.endMs
                    }
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
        qualityOptions.add(QualityOption("Auto", isAuto = true, isSelected = !hasVideoOverride))
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
