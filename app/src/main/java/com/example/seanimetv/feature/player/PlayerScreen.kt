package com.example.seanimetv.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.seanimetv.components.ExpressiveLoadingIndicator
import com.example.seanimetv.data.anilist.model.AniListMedia
import com.example.seanimetv.data.anilist.model.AniListMediaListEntry
import com.example.seanimetv.data.anilist.model.AniListUser
import com.example.seanimetv.data.anilist.model.MediaListStatus
import com.example.seanimetv.data.anilist.repository.AniListRepository
import com.example.seanimetv.data.metadata.SkipType
import com.example.seanimetv.data.plugin.model.Episode
import com.example.seanimetv.data.plugin.model.EpisodeServer
import com.example.seanimetv.ui.theme.SeanimeOnSurface
import com.example.seanimetv.ui.theme.SeanimeOnSurfaceMuted
import com.example.seanimetv.ui.theme.SeanimePrimary
import com.example.seanimetv.ui.theme.SeanimeTVTheme
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.seanimetv.data.player.SubtitleStyle
import com.example.seanimetv.ui.theme.SeanimeSurface
import kotlin.math.PI
import kotlin.math.sin
import com.example.seanimetv.components.formatPlaybackTime

/**
 * Full-screen video player with a cinematic TV control layer.
 *
 * D-pad model:
 * - Controls auto-hide after 4s. Any key press re-shows them and focuses the
 *   play/pause button.
 * - Hidden: Left/Right quick-seek ∓10s, OK shows controls, Back exits.
 * - Visible: focus lives on the transport row (Left/Right move between controls,
 *   OK activates); Down/Back hides the controls.
 * - The quality and subtitles buttons open a side settings sheet.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    episodeServer: EpisodeServer,
    mediaId: Int,
    episodeNumber: Int,
    title: String,
    episodeTitle: String = "",
    episodes: List<Episode>,
    onBack: () -> Unit = {},
    onPlayNext: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val uiState by viewModel.uiState.collectAsState()
    val exoPlayer by viewModel.exoPlayerFlow.collectAsState()
    // NOTE: viewModel.progress (500ms ticker) is deliberately NOT collected here —
    // it is collected inside the controls layer only, so the ticks recompose
    // nothing while the controls are hidden (critical on Chromecast HD).
    val qualities by viewModel.qualities.collectAsState()
    val subtitles by viewModel.subtitles.collectAsState()
    val audioTracks by viewModel.audioTracks.collectAsState()
    val subtitleStyle by viewModel.subtitleStyle.collectAsState()
    // Emits only on entering/leaving an AniSkip interval (not per tick).
    val activeSkip by viewModel.activeSkip.collectAsState()
    // Emits only on entering/leaving the final 2 minutes (not per tick).
    val inEndZone by viewModel.inEndZone.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val hasNextEpisode = (uiState as? PlayerUiState.Ready)?.hasNextEpisode == true

    var controlsVisible by remember { mutableStateOf(true) }
    // Bumped on every remote keypress while controls are visible — restarts the
    // auto-hide timer. Never key the timer on playback position: position ticks
    // every 500ms and would cancel the delay forever (controls never hide).
    var interactionTick by remember { mutableStateOf(0) }
    var openSheet by remember { mutableStateOf<String?>(null) } // "quality" | "subtitles" | "audio" | null
    // When true, D-pad arrows move the subtitles instead of navigating controls.
    var repositioning by remember { mutableStateOf(false) }

    val rootFocus = remember { FocusRequester() }
    val playPauseFocus = remember { FocusRequester() }

    LaunchedEffect(episodeServer) {
        viewModel.initialize(context, episodeServer, mediaId, episodeNumber, title, episodes)
    }
    DisposableEffect(viewModel) { onDispose { viewModel.close() } }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }
    LaunchedEffect(viewModel) { viewModel.playNextEvent.collect { onPlayNext(it) } }

    LaunchedEffect(controlsVisible, openSheet, interactionTick) {
        if (controlsVisible && openSheet == null) {
            delay(4_000)
            controlsVisible = false
        }
    }
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) runCatching { playPauseFocus.requestFocus() }
        else runCatching { rootFocus.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (controlsVisible) interactionTick++
                if (repositioning) {
                    when (event.key) {
                        Key.DirectionUp -> viewModel.nudgeSubtitle(0f, -12f)
                        Key.DirectionDown -> viewModel.nudgeSubtitle(0f, 12f)
                        Key.DirectionLeft -> viewModel.nudgeSubtitle(-12f, 0f)
                        Key.DirectionRight -> viewModel.nudgeSubtitle(12f, 0f)
                        Key.DirectionCenter, Key.Enter, Key.Back -> {
                            repositioning = false; openSheet = "subtitles"
                        }
                        else -> Unit
                    }
                    return@onPreviewKeyEvent true
                }
                if (openSheet != null) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Back ->
                        if (controlsVisible) { controlsVisible = false; true } else { onBack(); true }
                    else -> if (!controlsVisible) {
                        when (event.key) {
                            Key.DirectionLeft -> { viewModel.seekBy(-10_000); controlsVisible = true; true }
                            Key.DirectionRight -> { viewModel.seekBy(10_000); controlsVisible = true; true }
                            // OK inside an intro/outro interval skips it; in the
                            // final 2 minutes (no active skip) it jumps to the next
                            // episode; otherwise it shows the controls. Each pill on
                            // screen advertises the current OK action.
                            Key.DirectionCenter, Key.Enter -> {
                                when {
                                    activeSkip != null -> viewModel.skipCurrentInterval()
                                    inEndZone && hasNextEpisode -> onPlayNext(episodeNumber + 1)
                                    else -> controlsVisible = true
                                }
                                true
                            }
                            Key.DirectionUp, Key.DirectionDown -> { controlsVisible = true; true }
                            else -> false
                        }
                    } else {
                        if (event.key == Key.DirectionDown) { controlsVisible = false; true } else false
                    }
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                val pv = android.view.LayoutInflater.from(ctx)
                    .inflate(com.example.seanimetv.R.layout.player_view, null) as PlayerView
                pv.useController = false
                pv
            },
            update = { pv ->
                if (pv.player !== exoPlayer) pv.player = exoPlayer
                pv.subtitleView?.apply {
                    // Our style overrides any embedded cue styling / font sizes.
                    setApplyEmbeddedStyles(false)
                    setApplyEmbeddedFontSizes(false)
                    setStyle(
                        androidx.media3.ui.CaptionStyleCompat(
                            subtitleStyle.foregroundColor,
                            if (subtitleStyle.backgroundEnabled) 0x99000000.toInt() else android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                            androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            null
                        )
                    )
                    setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, subtitleStyle.textSizeSp)
                    translationX = with(density) { subtitleStyle.offsetXDp.dp.toPx() }
                    translationY = with(density) { subtitleStyle.offsetYDp.dp.toPx() }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        when (val state = uiState) {
            is PlayerUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ExpressiveLoadingIndicator(color = SeanimePrimary, size = 64.dp)
            }
            is PlayerUiState.Error -> Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Text("Couldn't play this episode", color = SeanimeOnSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(state.message, color = SeanimeOnSurfaceMuted, fontSize = 13.sp)
                    RetryButton { viewModel.initialize(context, episodeServer, mediaId, episodeNumber, title, episodes) }
                }
            }
            is PlayerUiState.Ready -> if (isBuffering) {
                // Mid-playback rebuffering — without this the frame just froze
                // with no feedback.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ExpressiveLoadingIndicator(color = SeanimePrimary, size = 64.dp)
                }
            }
        }

        // ── AniSkip pill — visible while the playhead is inside an intro/
        // outro interval and the controls are hidden; OK skips it.
        val skip = activeSkip
        if (!controlsVisible && skip != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 48.dp, bottom = 48.dp)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (skip.type == SkipType.INTRO) "OK  —  Skip intro" else "OK  —  Skip outro",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // ── Next-episode pill — the final 2 minutes, when there is a next
        // episode and no skip pill is showing; OK jumps ahead immediately.
        if (!controlsVisible && skip == null && inEndZone && hasNextEpisode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 48.dp, bottom = 48.dp)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "OK  —  Next episode",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // ── Control layer ─────────────────────────────────────────────────
        val ready = uiState as? PlayerUiState.Ready
        AnimatedVisibility(
            visible = controlsVisible && ready != null,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(220)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Collected HERE so the 500ms position ticks only recompose the
                // controls layer while it is actually visible.
                val progress by viewModel.progress.collectAsState()
                // Top scrim + title
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.75f),
                                1f to Color.Transparent
                            )
                        )
                )
                Column(modifier = Modifier.align(Alignment.TopStart).padding(start = 48.dp, top = 36.dp)) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (episodeTitle.isNotBlank()) {
                        Text(
                            text = episodeTitle,
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                    Text(
                        text = "Episode $episodeNumber",
                        color = SeanimePrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }

                // Bottom scrim + controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.88f)
                            )
                        )
                        .padding(start = 48.dp, end = 48.dp, bottom = 34.dp, top = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Scrubber(
                        positionMs = progress.positionMs,
                        durationMs = progress.durationMs,
                        bufferedMs = progress.bufferedMs,
                        isPlaying = progress.isPlaying
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TransportButton(Icons.Rounded.Replay10, "Rewind 10 seconds") { viewModel.seekBy(-10_000) }
                        Spacer(Modifier.width(12.dp))
                        TransportButton(
                            icon = if (progress.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (progress.isPlaying) "Pause" else "Play",
                            primary = true,
                            focusRequester = playPauseFocus
                        ) { viewModel.togglePlayPause() }
                        Spacer(Modifier.width(12.dp))
                        TransportButton(Icons.Rounded.Forward10, "Forward 10 seconds") { viewModel.seekBy(10_000) }

                        Spacer(Modifier.width(20.dp))
                        Text(
                            text = formatPlaybackTime(progress.positionMs),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "  /  ${formatPlaybackTime(progress.durationMs)}",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 14.sp
                        )

                        Spacer(Modifier.weight(1f))

                        SettingButton(
                            icon = Icons.Rounded.HighQuality,
                            label = qualities.firstOrNull { it.isSelected }?.label ?: "Auto"
                        ) { openSheet = "quality" }
                        Spacer(Modifier.width(10.dp))
                        SettingButton(
                            icon = Icons.Rounded.ClosedCaption,
                            label = subtitles.firstOrNull { it.isSelected }?.label ?: "Off"
                        ) { openSheet = "subtitles" }
                        // Audio picker only when there's a real choice (e.g. dub + sub).
                        if (audioTracks.size > 1) {
                            Spacer(Modifier.width(10.dp))
                            SettingButton(
                                icon = Icons.Rounded.Audiotrack,
                                label = audioTracks.firstOrNull { it.isSelected }?.label ?: "Audio"
                            ) { openSheet = "audio" }
                        }
                    }
                }
            }
        }

        // ── Settings sheet ────────────────────────────────────────────────
        AnimatedVisibility(
            visible = openSheet != null,
            enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { it / 8 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { it / 8 },
            modifier = Modifier.fillMaxSize()
        ) {
            when (openSheet) {
                "quality" -> SettingsSheet(
                    title = "Quality",
                    subtitle = "Video resolution",
                    items = qualities.map { it.label to it.isSelected },
                    onSelect = { i -> viewModel.selectQuality(qualities[i]); openSheet = null; controlsVisible = true },
                    onClose = { openSheet = null; controlsVisible = true }
                )
                "audio" -> SettingsSheet(
                    title = "Audio",
                    subtitle = "Audio track",
                    items = audioTracks.map { it.label to it.isSelected },
                    onSelect = { i -> viewModel.selectAudio(audioTracks[i]); openSheet = null; controlsVisible = true },
                    onClose = { openSheet = null; controlsVisible = true }
                )
                "subtitles" -> SubtitleSheet(
                    tracks = subtitles,
                    style = subtitleStyle,
                    onSelectTrack = { i -> viewModel.selectSubtitle(subtitles[i]) },
                    onSizeChange = { d -> viewModel.changeSubtitleSize(d) },
                    onColor = { c -> viewModel.setSubtitleColor(c) },
                    onToggleBackground = { viewModel.setSubtitleBackground(!subtitleStyle.backgroundEnabled) },
                    onReposition = { repositioning = true; openSheet = null },
                    onCenter = { viewModel.centerSubtitle() },
                    onReset = { viewModel.resetSubtitleStyle() },
                    onClose = { openSheet = null; controlsVisible = true }
                )
                else -> Box(Modifier.fillMaxSize())
            }
        }

        // ── Subtitle reposition mode ──────────────────────────────────────
        if (repositioning) {
            SubtitleRepositionOverlay(style = subtitleStyle)
        }
    }
}

// ---------------------------------------------------------------------------
// Scrubber
// ---------------------------------------------------------------------------


// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

private class PreviewAniListRepository : AniListRepository {
    override suspend fun getViewer(): Result<AniListUser> = Result.failure(UnsupportedOperationException())
    override suspend fun getMediaList(userId: Int, status: MediaListStatus): Result<List<AniListMediaListEntry>> =
        Result.failure(UnsupportedOperationException())
    override suspend fun getMediaById(id: Int): Result<AniListMedia> = Result.failure(UnsupportedOperationException())
    override suspend fun saveProgress(mediaId: Int, progress: Int, status: MediaListStatus): Result<Unit> =
        Result.success(Unit)
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
@OptIn(ExperimentalTvMaterial3Api::class)
fun PlayerScreenLoadingPreview() {
    val viewModel = PlayerViewModel(PreviewAniListRepository(), OkHttpClient())
    SeanimeTVTheme {
        PlayerScreen(
            viewModel = viewModel,
            episodeServer = EpisodeServer(server = "preview", headers = emptyMap(), videoSources = emptyList()),
            mediaId = 1,
            episodeNumber = 1,
            title = "Preview Anime",
            episodes = emptyList()
        )
    }
}
