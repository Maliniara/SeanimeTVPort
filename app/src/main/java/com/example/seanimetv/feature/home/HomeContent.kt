package com.example.seanimetv.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.seanimetv.components.AmbientBackdrop
import com.example.seanimetv.components.AnimeCard
import com.example.seanimetv.components.SectionHeader
import com.example.seanimetv.components.entranceReveal
import com.example.seanimetv.components.MediaCardRow
import com.example.seanimetv.components.SeriesPill
import com.example.seanimetv.components.rememberEntranceProgress
import com.example.seanimetv.components.LocalIsLowRamDevice
import com.example.seanimetv.data.anilist.model.MediaListStatus
import com.example.seanimetv.data.history.WatchHistoryEntry
import com.example.seanimetv.data.metadata.AniZipClient
import com.example.seanimetv.data.model.AnimeEntry
import com.example.seanimetv.ui.theme.SeanimeOnSurfaceMuted
import com.example.seanimetv.ui.theme.SeanimePrimary
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// ---------------------------------------------------------------------------
// Home section
// ---------------------------------------------------------------------------

@Composable
internal fun HomeContent(
    state: HomeUiState.Success,
    watchHistory: List<WatchHistoryEntry>,
    dismissedContinue: Set<Int>,
    onCardClick: (Int) -> Unit,
    onResumeClick: (Int, Int) -> Unit,
    onRemoveHistory: (Int) -> Unit,
    onDismissContinue: (Int) -> Unit,
    onChangeStatus: (Int, Int, MediaListStatus) -> Unit,
    aniZipClient: AniZipClient? = null
) {
    val allEntries = state.currentList + state.planningList
    // The hero pins the LAST WATCHED series (local history) and that series is
    // removed from the Continue Watching row below — otherwise the top of Home
    // duplicated the same show twice.
    val lastWatched = watchHistory.firstOrNull()
    val lastWatchedId = lastWatched?.anilistId
    val continueWatching = remember(state, lastWatchedId, dismissedContinue) {
        state.currentList
            .filterNot { it.id == lastWatchedId }
            // Series the user hid from this row via the card menu (view-only).
            .filterNot { it.id in dismissedContinue }
            // Drop series whose next episode hasn't aired yet — nothing new to
            // continue, so they don't belong in Continue Watching.
            .filterNot { entry ->
                entry.nextAiringEpisode?.let { entry.progress + 1 >= it } == true
            }
    }

    // The card the user long-pressed — drives the context menu overlay. Null = closed.
    var cardMenu by remember { mutableStateOf<CardMenuTarget?>(null) }
    // The series' real AniList progress (episodes completed), from the same lists
    // statusOf() reads. 0 when the series has no list entry yet, which is the
    // truthful value for an entry the status change is about to create.
    fun progressOf(id: Int): Int =
        (state.currentList + state.planningList + state.completedList +
            state.pausedList + state.droppedList)
            .firstOrNull { it.id == id }?.progress ?: 0

    fun statusOf(id: Int): MediaListStatus? = when {
        state.currentList.any { it.id == id } -> MediaListStatus.CURRENT
        state.planningList.any { it.id == id } -> MediaListStatus.PLANNING
        state.completedList.any { it.id == id } -> MediaListStatus.COMPLETED
        state.pausedList.any { it.id == id } -> MediaListStatus.PAUSED
        state.droppedList.any { it.id == id } -> MediaListStatus.DROPPED
        else -> null
    }
    var heroEntry by remember(state, lastWatchedId) {
        mutableStateOf(
            lastWatched?.let { h ->
                state.currentList.firstOrNull { it.id == h.anilistId } ?: AnimeEntry(
                    id = h.anilistId,
                    title = h.title,
                    coverImageUrl = h.coverImageUrl,
                    bannerImageUrl = h.bannerImageUrl
                )
            } ?: allEntries.firstOrNull()
        )
    }
    val heroButtonFocuser = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    // Snap initial focus to the hero Watch button so the TV focus system never
    // picks a card deeper in the scroll column and displaces the view on load.
    // Keyed on Unit, NOT state: a background data refresh must never steal
    // focus or reset the scroll position mid-browsing.
    LaunchedEffect(Unit) {
        try { heroButtonFocuser.requestFocus() } catch (_: Exception) {}
        scrollState.scrollTo(0)
    }

    // Timestamp of the last card-focus interaction — the backdrop rotation
    // stays out of the way while the user is actively browsing.
    var lastFocusAt by remember { mutableStateOf(0L) }
    fun focusHero(entry: AnimeEntry) {
        heroEntry = entry
        lastFocusAt = System.currentTimeMillis()
    }

    // Ambient backdrop rotation: every 60s the hero swaps to another Continue
    // Watching series; with fewer than 5 CW series, planning banners join the
    // pool. Each swap costs a full-screen banner decode + crossfade, so it is
    // skipped entirely on low-RAM boxes and paused during active browsing.
    // Keyed on Unit + rememberUpdatedState so refreshes don't restart the loop.
    val lowRam = LocalIsLowRamDevice.current
    val latestState by rememberUpdatedState(state)
    LaunchedEffect(Unit) {
        if (lowRam) return@LaunchedEffect
        while (true) {
            delay(60_000)
            if (System.currentTimeMillis() - lastFocusAt < 20_000) continue
            val s = latestState
            val pool = if (s.currentList.size >= 5) {
                s.currentList
            } else {
                s.currentList + s.planningList
            }
            val candidates = pool.filter { it.id != heroEntry?.id }
            if (candidates.isNotEmpty()) heroEntry = candidates.random()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // Full-bleed backdrop — spans the whole screen, including the strip
        // under the floating sidebar card
        AmbientBackdrop(imageUrl = heroEntry?.bannerImageUrl ?: heroEntry?.coverImageUrl)

        // Column+verticalScroll instead of LazyColumn — avoids the auto-scroll-to-focused-item
        // behavior that caused the ContinueWatching panel to bounce up/down when cards got focus.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            HeroSection(
                entry = heroEntry,
                onWatchClick = { heroEntry?.let { onCardClick(it.id) } },
                onDetailsClick = { heroEntry?.let { onCardClick(it.id) } },
                watchButtonFocuser = heroButtonFocuser
            )

            // Rows cascade in after the hero — graphicsLayer only, so cards are
            // focusable from the first frame. Inset past the sidebar card.
            Column(modifier = Modifier.padding(start = 76.dp)) {
                if (watchHistory.isNotEmpty()) {
                    Box(Modifier.entranceReveal(rememberEntranceProgress(delayMillis = 100))) {
                        WatchHistoryRow(
                            entries = watchHistory,
                            onResume = { h -> onResumeClick(h.anilistId, h.episodeNumber) },
                            onCardFocus = { h ->
                                focusHero(
                                    AnimeEntry(
                                        id = h.anilistId,
                                        title = h.title,
                                        coverImageUrl = h.coverImageUrl,
                                        bannerImageUrl = h.bannerImageUrl
                                    )
                                )
                            },
                            onCardLongPress = { h ->
                                cardMenu = CardMenuTarget(
                                    anilistId = h.anilistId,
                                    title = h.title,
                                    // The AniList list progress (episodes COMPLETED), never
                                    // the episode currently being watched — sending the
                                    // latter would silently mark that episode watched just
                                    // because the user changed the series' status.
                                    progress = progressOf(h.anilistId),
                                    fromHistory = true
                                )
                            }
                        )
                    }
                }

                if (continueWatching.isNotEmpty()) {
                    Box(Modifier.entranceReveal(rememberEntranceProgress(delayMillis = 150))) {
                        ContinueWatchingRow(
                            entries = continueWatching,
                            focusedEntry = heroEntry,
                            onCardClick = onCardClick,
                            onCardFocus = { focusHero(it) },
                            onCardLongPress = { e ->
                                cardMenu = CardMenuTarget(
                                    anilistId = e.id,
                                    title = e.title,
                                    progress = e.progress,
                                    fromHistory = false
                                )
                            },
                            aniZipClient = aniZipClient
                        )
                    }
                }

                if (state.planningList.isNotEmpty()) {
                    Box(Modifier.entranceReveal(rememberEntranceProgress(delayMillis = 280))) {
                        SectionRow(
                            title = "Planning",
                            entries = state.planningList,
                            onCardClick = onCardClick,
                            onCardFocus = { focusHero(it) }
                        )
                    }
                }

                if (state.recommendedList.isNotEmpty()) {
                    Box(Modifier.entranceReveal(rememberEntranceProgress(delayMillis = 400))) {
                        SectionRow(
                            title = "Recommended for You",
                            entries = state.recommendedList,
                            onCardClick = onCardClick
                        )
                    }
                }

                if (allEntries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No anime in your list yet.\nAdd some on AniList!",
                            color = SeanimeOnSurfaceMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(48.dp))
            }
        }

        cardMenu?.let { target ->
            HomeCardMenu(
                title = target.title,
                removeLabel = if (target.fromHistory) "Remove from Recently watched"
                    else "Hide from Continue watching",
                currentStatus = statusOf(target.anilistId),
                onRemove = {
                    if (target.fromHistory) onRemoveHistory(target.anilistId)
                    else onDismissContinue(target.anilistId)
                },
                onChangeStatus = { status -> onChangeStatus(target.anilistId, target.progress, status) },
                onDismiss = { cardMenu = null }
            )
        }
    }
}

/** The Home card targeted by the long-press context menu (see [HomeCardMenu]). */
private data class CardMenuTarget(
    val anilistId: Int,
    val title: String,
    val progress: Int,
    val fromHistory: Boolean
)

// ---------------------------------------------------------------------------
// Hero section
// ---------------------------------------------------------------------------

@Composable
private fun HeroSection(
    entry: AnimeEntry?,
    onWatchClick: () -> Unit,
    onDetailsClick: () -> Unit,
    watchButtonFocuser: FocusRequester? = null
) {
    val revealTitle = rememberEntranceProgress()
    val revealMeta = rememberEntranceProgress(delayMillis = 120)
    val revealButtons = rememberEntranceProgress(delayMillis = 240)

    // Overlay-only hero: the banner and all scrims live in the full-screen
    // backdrop behind the scroll column — this area only holds the
    // title/metadata/buttons block.
    Box(modifier = Modifier.fillMaxWidth().height(340.dp)) {
        if (entry != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 108.dp, bottom = 28.dp, end = 160.dp)
            ) {
                Text(
                    text = entry.title,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 34.sp,
                    modifier = Modifier.entranceReveal(revealTitle)
                )
                val nextEp = entry.progress + 1
                val totalEp = entry.totalEpisodes
                Text(
                    text = if (totalEp != null) "Episode $nextEp of $totalEp" else "Episode $nextEp",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .entranceReveal(revealMeta)
                )
                // Thin watch-progress line under the episode label
                if (totalEp != null && entry.progress > 0) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .width(220.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.18f))
                            .entranceReveal(revealMeta)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = (entry.progress.toFloat() / totalEp).coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(SeanimePrimary)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.entranceReveal(revealButtons)
                ) {
                    SeriesPill(
                        label = "▶  Watch",
                        onClick = onWatchClick,
                        modifier = if (watchButtonFocuser != null) {
                            Modifier.focusRequester(watchButtonFocuser)
                        } else Modifier
                    )
                    SeriesPill(label = "Details", onClick = onDetailsClick)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Generic section row (Planning, etc.)
// ---------------------------------------------------------------------------

@Composable
private fun SectionRow(
    title: String,
    entries: List<AnimeEntry>,
    onCardClick: (Int) -> Unit,
    onCardFocus: (AnimeEntry) -> Unit = {}
) {
    Column(modifier = Modifier.padding(bottom = 28.dp)) {
        SectionHeader(
            text = title,
            modifier = Modifier.padding(start = 32.dp, bottom = 10.dp)
        )
        MediaCardRow(startInset = 32.dp, itemSpacing = 10.dp) {
            itemsIndexed(entries, key = { _, entry -> entry.id }) { _, entry ->
                AnimeCard(
                    title = entry.title,
                    coverImageUrl = entry.coverImageUrl,
                    onClick = { onCardClick(entry.id) },
                    onFocus = { onCardFocus(entry) }
                )
            }
        }
    }
}

