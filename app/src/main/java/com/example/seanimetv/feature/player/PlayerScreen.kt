package com.example.seanimetv.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.seanimetv.data.player.SubtitleStyle
import com.example.seanimetv.data.plugin.model.Episode
import com.example.seanimetv.data.plugin.model.EpisodeServer
import com.example.seanimetv.ui.theme.SeanimeOnSurface
import com.example.seanimetv.ui.theme.SeanimeOnSurfaceMuted
import com.example.seanimetv.ui.theme.SeanimePrimary
import com.example.seanimetv.ui.theme.SeanimeSurface
import com.example.seanimetv.ui.theme.SeanimeTVTheme
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

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
                            text = formatTime(progress.positionMs),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "  /  ${formatTime(progress.durationMs)}",
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

@Composable
private fun Scrubber(positionMs: Long, durationMs: Long, bufferedMs: Long, isPlaying: Boolean) {
    val played = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val buffered = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    // Material 3 Expressive: the played portion is a WAVE that ripples while playing
    // and settles flat when paused (amplitude animates); its phase scrolls forever.
    val amplitude by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(450),
        label = "waveAmp"
    )
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase"
    )

    val inactiveColor = Color.White.copy(alpha = 0.20f)
    val bufferedColor = Color.White.copy(alpha = 0.32f)

    Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
        val w = size.width
        val cy = size.height / 2f
        val stroke = 5.dp.toPx()
        val thumbW = 6.dp.toPx()
        val thumbH = 22.dp.toPx()
        val gap = 8.dp.toPx()
        val amp = amplitude * 4.5.dp.toPx()
        val playedX = w * played
        val activeEnd = (playedX - gap).coerceAtLeast(0f)
        val afterThumb = (playedX + gap).coerceAtMost(w)

        // Inactive track (right of the thumb), with a gap around the thumb.
        if (afterThumb < w) {
            drawLine(inactiveColor, Offset(afterThumb, cy), Offset(w, cy), stroke, StrokeCap.Round)
        }
        // Buffered ahead of the playhead.
        val bufferedX = (w * buffered).coerceAtMost(w)
        if (bufferedX > afterThumb) {
            drawLine(bufferedColor, Offset(afterThumb, cy), Offset(bufferedX, cy), stroke, StrokeCap.Round)
        }
        // Active WAVE (left of the thumb).
        if (activeEnd > 1f) {
            val path = Path()
            val wavelength = 24.dp.toPx()
            val step = 3.dp.toPx()
            path.moveTo(0f, cy)
            var x = 0f
            while (x <= activeEnd) {
                val y = cy + amp * sin((x / wavelength) * 2f * PI.toFloat() + phase)
                path.lineTo(x, y)
                x += step
            }
            drawPath(path = path, color = SeanimePrimary, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
        // Thumb — a rounded pill.
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(playedX - thumbW / 2f, cy - thumbH / 2f),
            size = Size(thumbW, thumbH),
            cornerRadius = CornerRadius(thumbW / 2f, thumbW / 2f)
        )
    }
}

// ---------------------------------------------------------------------------
// Transport & setting buttons
// ---------------------------------------------------------------------------

@Composable
private fun TransportButton(
    icon: ImageVector,
    contentDescription: String,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, tween(140), label = "transportScale")
    val diameter = if (primary) 64.dp else 50.dp
    val iconSize = if (primary) 34.dp else 26.dp

    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(diameter)
            .clip(CircleShape)
            .background(
                when {
                    focused -> SeanimePrimary
                    primary -> Color.White.copy(alpha = 0.16f)
                    else -> Color.White.copy(alpha = 0.10f)
                }
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction),
        contentAlignment = Alignment.Center
    ) {
        // Expressive play↔pause morph: the icon scales + fades through on change.
        AnimatedContent(
            targetState = icon,
            transitionSpec = {
                (fadeIn(tween(200)) + scaleIn(initialScale = 0.6f, animationSpec = tween(200)))
                    .togetherWith(fadeOut(tween(200)) + scaleOut(targetScale = 0.6f, animationSpec = tween(200)))
            },
            label = "transportIcon"
        ) { ic ->
            Icon(ic, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
private fun SettingButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (focused) SeanimePrimary else Color.White.copy(alpha = 0.12f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RetryButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Box(
        modifier = Modifier
            .focusRequester(fr)
            .clip(RoundedCornerShape(24.dp))
            .background(if (focused) SeanimePrimary else Color.White.copy(alpha = 0.14f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 24.dp, vertical = 11.dp)
    ) {
        Text("Try again", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------------------
// Settings sheet (quality / subtitles)
// ---------------------------------------------------------------------------

@Composable
private fun SettingsSheet(
    title: String,
    subtitle: String,
    items: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.45f to Color.Black.copy(alpha = 0.35f),
                    1f to Color.Black.copy(alpha = 0.7f)
                )
            )
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Back) { onClose(); true } else false
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .fillMaxHeight()
                .background(SeanimeSurface.copy(alpha = 0.97f))
                .padding(horizontal = 28.dp, vertical = 40.dp)
        ) {
            Text(title, color = SeanimeOnSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = SeanimeOnSurfaceMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 22.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(items.size) { i ->
                    val (label, selected) = items[i]
                    SheetRow(label, selected, if (i == 0) firstFocus else null) { onSelect(i) }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(label: String, selected: Boolean, focusRequester: FocusRequester?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) SeanimePrimary else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (focused) Color.White else SeanimeOnSurface.copy(alpha = if (selected) 1f else 0.8f),
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "Selected",
                tint = if (focused) Color.White else SeanimePrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Subtitle sheet — track + style (size, colour, background, position)
// ---------------------------------------------------------------------------

private val SUBTITLE_COLORS = listOf(
    0xFFFFFFFF.toInt(), // white
    0xFFFFEB3B.toInt(), // yellow
    0xFF00E5FF.toInt(), // cyan
    0xFF69F0AE.toInt(), // green
    0xFFFFAB40.toInt()  // orange
)

@Composable
private fun SubtitleSheet(
    tracks: List<SubtitleOption>,
    style: SubtitleStyle,
    onSelectTrack: (Int) -> Unit,
    onSizeChange: (Float) -> Unit,
    onColor: (Int) -> Unit,
    onToggleBackground: () -> Unit,
    onReposition: () -> Unit,
    onCenter: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.45f to Color.Black.copy(alpha = 0.35f),
                    1f to Color.Black.copy(alpha = 0.7f)
                )
            )
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Back) { onClose(); true } else false
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .fillMaxHeight()
                .background(SeanimeSurface.copy(alpha = 0.97f))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text("Subtitles", color = SeanimeOnSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)

            SectionLabel("Track")
            tracks.forEachIndexed { i, t ->
                SheetRow(t.label, t.isSelected, if (i == 0) firstFocus else null) { onSelectTrack(i) }
            }

            SectionLabel("Text size")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StepButton("–") { onSizeChange(-2f) }
                Text("${style.textSizeSp.toInt()} sp", color = SeanimeOnSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                StepButton("+") { onSizeChange(2f) }
            }

            SectionLabel("Color")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SUBTITLE_COLORS.forEach { c ->
                    ColorSwatch(color = c, selected = c == style.foregroundColor) { onColor(c) }
                }
            }

            SectionLabel("Background")
            SheetActionRow(
                label = "Background box",
                trailing = if (style.backgroundEnabled) "On" else "Off",
                onClick = onToggleBackground
            )

            SectionLabel("Position")
            SheetActionRow("Reposition subtitles", onClick = onReposition)
            SheetActionRow("Center horizontally", onClick = onCenter)
            SheetActionRow("Restore defaults", onClick = onReset)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = SeanimeOnSurfaceMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (focused) SeanimePrimary else Color.White.copy(alpha = 0.12f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ColorSwatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(
                width = if (focused || selected) 3.dp else 1.dp,
                color = if (focused) SeanimePrimary else if (selected) Color.White else Color.White.copy(alpha = 0.25f),
                shape = CircleShape
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
    )
}

@Composable
private fun SheetActionRow(label: String, trailing: String? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) SeanimePrimary else Color.White.copy(alpha = 0.06f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = if (focused) Color.White else SeanimeOnSurface, fontSize = 15.sp)
        if (trailing != null) {
            Text(trailing, color = if (focused) Color.White else SeanimePrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SubtitleRepositionOverlay(style: SubtitleStyle) {
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 44.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 22.dp, vertical = 11.dp)
        ) {
            Text(
                "Move subtitles with the D-pad  ·  OK to finish",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .offset(x = style.offsetXDp.dp, y = style.offsetYDp.dp)
                .then(
                    if (style.backgroundEnabled)
                        Modifier.clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.6f))
                    else Modifier
                )
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                "Subtitle preview",
                color = Color(style.foregroundColor),
                fontSize = style.textSizeSp.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

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
