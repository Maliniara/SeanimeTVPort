package com.example.seanimetv.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.seanimetv.components.SectionHeader
import com.example.seanimetv.components.MediaCardRow
import com.example.seanimetv.data.history.WatchHistoryEntry
import com.example.seanimetv.data.metadata.AniZipClient
import com.example.seanimetv.data.model.AnimeEntry
import com.example.seanimetv.ui.theme.ExpressiveMotion
import com.example.seanimetv.ui.theme.ExpressiveShapes
import com.example.seanimetv.ui.theme.SeanimeOnSurface
import com.example.seanimetv.ui.theme.SeanimeOnSurfaceMuted
import com.example.seanimetv.ui.theme.SeanimePrimary
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.seanimetv.components.formatPlaybackTime

// ---------------------------------------------------------------------------
// Continue Watching — episode-style wide cards (Image #1 reference)
// ---------------------------------------------------------------------------

@Composable
internal fun ContinueWatchingRow(
    entries: List<AnimeEntry>,
    focusedEntry: AnimeEntry?,
    onCardClick: (Int) -> Unit,
    onCardFocus: (AnimeEntry) -> Unit,
    onCardLongPress: (AnimeEntry) -> Unit,
    aniZipClient: AniZipClient? = null
) {
    Column(modifier = Modifier.padding(bottom = 32.dp)) {
        Column(modifier = Modifier.padding(start = 32.dp, bottom = 12.dp)) {
            SectionHeader("Continue watching")
            Text(
                text = focusedEntry?.title ?: entries.firstOrNull()?.title ?: "",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = SeanimeOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp, start = 12.dp)
            )
        }

        MediaCardRow(startInset = 32.dp, itemSpacing = 16.dp) {
            itemsIndexed(entries, key = { _, entry -> entry.id }) { _, entry ->
                EpisodeCard(
                    entry = entry,
                    onClick = { onCardClick(entry.id) },
                    onFocus = { onCardFocus(entry) },
                    onLongClick = { onCardLongPress(entry) },
                    aniZipClient = aniZipClient
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeCard(
    entry: AnimeEntry,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    onLongClick: () -> Unit,
    aniZipClient: AniZipClient? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Real per-episode thumbnail (Denshi look): lazily pull the NEXT episode's
    // image from ani.zip. Falls back to the banner until (or unless) it loads.
    var episodeThumb by remember(entry.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(entry.id, entry.progress) {
        val client = aniZipClient ?: return@LaunchedEffect
        val next = entry.progress + 1
        episodeThumb = client.getEpisodes(entry.id)[next]?.image
    }

    // Defer the hero-entry update to after composition completes. Calling onFocus()
    // directly during composition mutates heroEntry while HeroSection reads it in the
    // same composition pass — Compose restarts the composition, looping until the
    // animation frame settles, which produces the visible up/down bounce.
    // The 150ms delay also debounces rapid D-pad navigation so the hero image only
    // updates when the user actually pauses on a card.
    LaunchedEffect(isFocused) {
        if (isFocused) {
            // 300ms matches the backdrop debounce: a vertical row change never
            // swaps the hero mid-scroll (that recomposition was the frame drop
            // when crossing between rows).
            delay(300)
            onFocus()
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = ExpressiveMotion.spatialFast(),
        label = "episodeCardScale"
    )

    val nextEp = entry.progress + 1
    val totalEp = entry.totalEpisodes
    // AniList told us which episode airs NEXT — anything from that number up
    // hasn't been published yet, so the card must not imply it's watchable.
    val nextUnaired = entry.nextAiringEpisode?.let { nextEp >= it } == true

    Column(
        modifier = Modifier
            .width(280.dp)
            // graphicsLayer keeps layout size stable — scale is draw-only, no layout bounce
            .drawWithContent {
                val s = scale
                scale(scale = s, pivot = center) {
                    this@drawWithContent.drawContent()
                }
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .focusable(interactionSource = interactionSource)
    ) {
        // 16:9 thumbnail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(158.dp)
                .clip(ExpressiveShapes.medium)
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
                    color = SeanimePrimary,
                    shape = ExpressiveShapes.medium
                )
        ) {
            val imageUrl = episodeThumb ?: entry.bannerImageUrl ?: entry.coverImageUrl
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Bottom gradient + episode label
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))
                    )
            )
            // Progress bar at bottom
            if (totalEp != null && entry.progress > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = (entry.progress.toFloat() / totalEp).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(SeanimePrimary)
                    )
                }
            }
        }

        // Metadata below card
        Column(modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Episode",
                    fontSize = 11.sp,
                    color = SeanimeOnSurfaceMuted
                )
                Text(
                    text = if (totalEp != null) "$nextEp / $totalEp" else "$nextEp",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (nextUnaired) SeanimeOnSurfaceMuted else SeanimeOnSurface
                )
                Text(
                    text = "·",
                    fontSize = 11.sp,
                    color = SeanimeOnSurfaceMuted
                )
                Text(
                    text = if (nextUnaired) "Not aired yet" else "24m",
                    fontSize = 11.sp,
                    fontWeight = if (nextUnaired) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (nextUnaired) SeanimePrimary else SeanimeOnSurfaceMuted
                )
            }
            Text(
                text = entry.title,
                fontSize = 11.sp,
                color = SeanimeOnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Recently watched — local playback history with resume position (mm:ss)
// ---------------------------------------------------------------------------

@Composable
internal fun WatchHistoryRow(
    entries: List<WatchHistoryEntry>,
    onResume: (WatchHistoryEntry) -> Unit,
    onCardFocus: (WatchHistoryEntry) -> Unit,
    onCardLongPress: (WatchHistoryEntry) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 32.dp)) {
        Column(modifier = Modifier.padding(start = 32.dp, bottom = 12.dp)) {
            SectionHeader("Recently watched")
        }
        MediaCardRow(startInset = 32.dp, itemSpacing = 16.dp) {
            itemsIndexed(entries, key = { _, entry -> entry.anilistId }) { _, entry ->
                WatchHistoryCard(
                    entry = entry,
                    onClick = { onResume(entry) },
                    onFocus = { onCardFocus(entry) },
                    onLongClick = { onCardLongPress(entry) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WatchHistoryCard(
    entry: WatchHistoryEntry,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(isFocused) { if (isFocused) { delay(300); onFocus() } }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(150),
        label = "historyCardScale"
    )
    val fraction = if (entry.durationMs > 0)
        (entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .width(280.dp)
            .drawWithContent {
                val s = scale
                scale(scale = s, pivot = center) { this@drawWithContent.drawContent() }
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .focusable(interactionSource = interactionSource)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(158.dp)
                .clip(ExpressiveShapes.medium)
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
                    color = SeanimePrimary,
                    shape = ExpressiveShapes.medium
                )
        ) {
            AsyncImage(
                model = entry.bannerImageUrl ?: entry.coverImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f)))
            )
            // Resume progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = fraction)
                        .fillMaxHeight()
                        .background(SeanimePrimary)
                )
            }
        }

        Column(modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Episode", fontSize = 11.sp, color = SeanimeOnSurfaceMuted)
                Text("${entry.episodeNumber}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SeanimeOnSurface)
                Text("·", fontSize = 11.sp, color = SeanimeOnSurfaceMuted)
                Text(formatPlaybackTime(entry.positionMs), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SeanimePrimary)
            }
            Text(
                text = entry.title,
                fontSize = 11.sp,
                color = SeanimeOnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


