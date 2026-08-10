package com.example.seanimetv.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import coil.compose.AsyncImage
import com.example.seanimetv.components.ActionButton
import com.example.seanimetv.components.AmbientBackdrop
import com.example.seanimetv.components.AnimeCard
import com.example.seanimetv.components.PageTitle
import com.example.seanimetv.components.SectionHeader
import com.example.seanimetv.components.ExpressiveLoadingIndicator
import com.example.seanimetv.components.entranceReveal
import com.example.seanimetv.components.MediaCardRow
import com.example.seanimetv.components.SeriesPill
import com.example.seanimetv.components.horizontalFadingEdges
import com.example.seanimetv.components.rememberEntranceProgress
import com.example.seanimetv.components.LibraryTab
import com.example.seanimetv.components.LocalIsLowRamDevice
import com.example.seanimetv.components.NavSection
import com.example.seanimetv.components.SideNavigation
import com.example.seanimetv.data.anilist.model.AniListMedia
import com.example.seanimetv.data.anilist.model.AniListMediaListEntry
import com.example.seanimetv.data.anilist.model.AniListUser
import com.example.seanimetv.data.anilist.model.MediaListStatus
import com.example.seanimetv.data.anilist.repository.AniListRepository
import com.example.seanimetv.data.history.WatchHistoryEntry
import com.example.seanimetv.data.metadata.AniZipClient
import com.example.seanimetv.data.model.AnimeEntry
import com.example.seanimetv.ui.theme.ExpressiveMotion
import com.example.seanimetv.ui.theme.ExpressiveShapes
import com.example.seanimetv.ui.theme.SeanimeBackground
import com.example.seanimetv.ui.theme.SeanimeBorder
import com.example.seanimetv.ui.theme.SeanimeOnSurface
import com.example.seanimetv.ui.theme.SeanimeOnSurfaceMuted
import com.example.seanimetv.ui.theme.SeanimePrimary
import com.example.seanimetv.ui.theme.SeanimeSurface
import com.example.seanimetv.ui.theme.SeanimeTVTheme

private const val HOME_REFRESH_INTERVAL_MS = 5 * 60 * 1000L

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    aniListRepository: AniListRepository,
    onSettingsClick: () -> Unit = {},
    onCardClick: (Int) -> Unit = {},
    onResumeClick: (Int, Int) -> Unit = { _, _ -> },
    extensionsPanel: @Composable () -> Unit = {},
    aniZipClient: AniZipClient? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState()
    val dismissedContinue by viewModel.dismissedContinue.collectAsState()

    // Periodic AniList refresh — lifecycle-aware, so it runs only while this
    // screen is actually STARTED. A TV box keeps the process alive for days;
    // an unconditional ViewModel loop would keep hitting the network (and the
    // AniList rate limit) in the background forever.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(HOME_REFRESH_INTERVAL_MS)
                viewModel.refresh()
            }
        }
    }

    // rememberSaveable: the section survives navigating away (series detail)
    // and back — otherwise Back from a series opened in Discovery/Library
    // would land on Home instead of the screen the user came from.
    var selectedSection by rememberSaveable { mutableStateOf(NavSection.HOME) }
    var selectedLibraryTab by rememberSaveable { mutableStateOf(LibraryTab.WATCHING) }
    var sidebarExpanded by remember { mutableStateOf(false) }

    // Back from any section returns to Home first; only Back from Home exits
    // the app. Without this, Back left the user "on the sidebar" as the last
    // stop before exit.
    BackHandler(enabled = selectedSection != NavSection.HOME) {
        selectedSection = NavSection.HOME
    }

    // Box layout: content fills full width, sidebar overlays on top at the left edge
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SeanimeBackground)
    ) {
        // Focus target for exiting the sidebar with D-pad RIGHT: requesting
        // focus on this group delegates to its first focusable child (hero
        // button on HOME, first control on other sections).
        val contentAreaFocuser = remember { FocusRequester() }

        // SOFTLOCK RECOVERY. On a weak TV box a focused card can be removed
        // from composition (lazy-row recycle, background refresh, section
        // swap). Compose then holds NO focus and every D-pad press is dead.
        // We track whether the content subtree holds focus and, whenever it
        // silently loses it while the sidebar isn't the intended owner, pull
        // focus back in — with frame-waited retries so we never request onto a
        // not-yet-attached node (the previous race that stranded focus).
        var contentHasFocus by remember { mutableStateOf(false) }

        // On section change, hand focus into the new section once it attaches.
        LaunchedEffect(selectedSection) {
            withFrameNanos { }
            runCatching { contentAreaFocuser.requestFocus() }
        }

        // BULLETPROOF SOFTLOCK RECOVERY. A single continuous loop: whenever the
        // content holds no focus AND the sidebar isn't taking it, pull focus
        // back to the section's first live child. Keeps retrying until it
        // sticks (contentHasFocus turns true) — the previous edge-triggered
        // version fired only once and, if that landed on a recycled node,
        // stranded focus forever. withFrameNanos suspends until the next frame,
        // so an idle, correctly-focused UI costs nothing. The two-frame settle
        // lets the sidebar's own onFocusChanged flip before we decide focus was
        // actually stranded (otherwise we'd yank it back as you enter the bar).
        val sidebarFocusedNow by rememberUpdatedState(sidebarExpanded)
        LaunchedEffect(Unit) {
            while (true) {
                withFrameNanos { }
                if (!contentHasFocus && !sidebarFocusedNow) {
                    withFrameNanos { }
                    withFrameNanos { }
                    if (!contentHasFocus && !sidebarFocusedNow) {
                        runCatching { contentAreaFocuser.requestFocus() }
                    }
                }
            }
        }

        // Content area — HOME is full-bleed so its backdrop banner runs under
        // the floating sidebar card; the other sections keep the 76dp inset
        // (12dp card margin + 56dp collapsed card + 8dp gap)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(contentAreaFocuser)
                .onFocusChanged { focusState ->
                    // Just record the state — the recovery LaunchedEffect above
                    // decides whether focus was stranded or moved to the sidebar.
                    contentHasFocus = focusState.hasFocus
                }
                // NO focusRestorer. It restores focus onto the LAST-focused
                // child, which after a section swap is a dead node — and the
                // dead-node restore is exactly what stranded focus and
                // softlocked the app, repeatedly. A bare focusGroup sends
                // requestFocus() to the section's first LIVE child instead.
                // Trade-off: returning from the sidebar lands on the first row,
                // not the exact row you left. Stability wins.
                .focusGroup()
        ) {
            when (val state = uiState) {
                is HomeUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ExpressiveLoadingIndicator(size = 56.dp)
                    }
                }

                is HomeUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = state.message,
                                color = SeanimeOnSurfaceMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 48.dp)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ActionButton(label = "Retry", onClick = { viewModel.retry() })
                                ActionButton(label = "Settings", onClick = onSettingsClick)
                            }
                        }
                    }
                }

                is HomeUiState.Success -> {
                    val userMediaIds = remember(state) {
                        (state.currentList + state.planningList + state.completedList +
                            state.droppedList + state.pausedList).map { it.id }.toSet()
                    }
                    val sectionInset = Modifier.fillMaxSize().padding(start = 76.dp)
                    // Library entrance choreography plays once per session —
                    // survives section switches AND item recycling in its lazy list
                    var libraryIntroPlayed by rememberSaveable { mutableStateOf(false) }
                    // Material 3 Expressive section transition: the incoming panel
                    // springs up + scales in with a fade while the old one fades out.
                    AnimatedContent(
                        targetState = selectedSection,
                        transitionSpec = {
                            val spatial = spring<Float>(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                            (fadeIn(animationSpec = tween(220)) +
                                scaleIn(initialScale = 0.97f, animationSpec = spatial) +
                                slideInVertically(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ) { it / 14 })
                                .togetherWith(fadeOut(animationSpec = tween(120)))
                        },
                        label = "sectionTransition"
                    ) { section ->
                        when (section) {
                            NavSection.HOME -> HomeContent(
                                state = state,
                                watchHistory = watchHistory,
                                dismissedContinue = dismissedContinue,
                                onCardClick = onCardClick,
                                onResumeClick = onResumeClick,
                                onRemoveHistory = viewModel::removeFromHistory,
                                onDismissContinue = viewModel::dismissFromContinue,
                                onChangeStatus = viewModel::changeStatus,
                                aniZipClient = aniZipClient
                            )
                            NavSection.LIBRARY -> LibraryContent(
                                state = state,
                                onCardClick = onCardClick,
                                introPlayed = libraryIntroPlayed,
                                onIntroFinished = { libraryIntroPlayed = true }
                            )
                            NavSection.SEARCH -> Box(sectionInset) {
                                AniListSearchScreen(
                                    aniListRepository = aniListRepository,
                                    onCardClick = onCardClick
                                )
                            }
                            NavSection.SCHEDULE -> Box(sectionInset) {
                                AiringScheduleScreen(
                                    aniListRepository = aniListRepository,
                                    userMediaIds = userMediaIds
                                )
                            }
                            NavSection.ANILIST -> AniListBrowseScreen(
                                aniListRepository = aniListRepository,
                                onCardClick = onCardClick
                            )
                            NavSection.EXTENSIONS -> Box(sectionInset) { extensionsPanel() }
                        }
                    }
                }
            }
        }

        // Sidebar overlays on top — expands from 56dp to 220dp without pushing content
        SideNavigation(
            selectedSection = selectedSection,
            expanded = sidebarExpanded,
            onExpandedChange = { sidebarExpanded = it },
            // Focus hand-off (which also collapses the sidebar) happens in the
            // LaunchedEffect above, AFTER the selected section has composed.
            onSectionSelected = { selectedSection = it },
            onSettingsClick = onSettingsClick,
            onExitRight = {
                try { contentAreaFocuser.requestFocus() } catch (_: Exception) {}
            },
            modifier = Modifier.fillMaxHeight()
        )
    }
}

// ---------------------------------------------------------------------------
// Home section
// ---------------------------------------------------------------------------

@Composable
private fun HomeContent(
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
                                    progress = h.episodeNumber,
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
// Continue Watching — episode-style wide cards (Image #1 reference)
// ---------------------------------------------------------------------------

@Composable
private fun ContinueWatchingRow(
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
private fun WatchHistoryRow(
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
                Text(formatTimestamp(entry.positionMs), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SeanimePrimary)
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

/** Formats a millisecond position as m:ss (or h:mm:ss past an hour). */
private fun formatTimestamp(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

// ---------------------------------------------------------------------------
// Library — sections with LazyRow + Show All grid
// ---------------------------------------------------------------------------

private data class LibrarySection(val title: String, val entries: List<AnimeEntry>)

/** Sub-panels of the My Library screen, toggled by the pill switch (mirrors /extensions). */
private enum class LibrarySubTab(val label: String) { LISTS("Library"), STATS("Stats") }

@Composable
private fun LibraryContent(
    state: HomeUiState.Success,
    onCardClick: (Int) -> Unit,
    introPlayed: Boolean = true,
    onIntroFinished: () -> Unit = {}
) {
    var expandedSection by remember { mutableStateOf<String?>(null) }
    var subTab by rememberSaveable { mutableStateOf(LibrarySubTab.LISTS) }

    // After the intro window, mark it done — items recycled by the lazy list
    // afterwards render instantly instead of replaying their reveal (which
    // caused stutter every time the user moved between sections).
    LaunchedEffect(Unit) {
        delay(1600)
        onIntroFinished()
    }

    val sections = remember(state) {
        listOf(
            LibrarySection("Watching", state.currentList),
            LibrarySection("Planning", state.planningList),
            LibrarySection("Completed", state.completedList),
            LibrarySection("Dropped", state.droppedList),
            LibrarySection("Paused", state.pausedList)
        ).filter { it.entries.isNotEmpty() }
    }

    val allEntries = remember(sections) { sections.flatMap { it.entries } }
    var featured by remember(state) { mutableStateOf(allEntries.firstOrNull()) }

    if (sections.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Your library is empty", color = SeanimeOnSurfaceMuted, fontSize = 14.sp)
        }
        return
    }

    // Same visual model as Home: full-bleed backdrop reacting to the focused
    // card, content rows inset past the floating sidebar card.
    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackdrop(imageUrl = featured?.bannerImageUrl ?: featured?.coverImageUrl)

        // Column+verticalScroll instead of LazyColumn — the lazy container's
        // scroll-position estimation for freshly (re)composed items caused a
        // small vertical bounce when focus entered a row; a plain column has
        // exact positions and also never recycles (no re-animation, ever).
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 28.dp, bottom = 48.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(start = 108.dp, bottom = 14.dp)
                    .then(
                        if (introPlayed) Modifier
                        else Modifier.entranceReveal(rememberEntranceProgress())
                    )
            ) {
                PageTitle("My Library")
                Text(
                    text = "${allEntries.size} titles across ${sections.size} lists",
                    fontSize = 12.sp,
                    color = SeanimeOnSurfaceMuted,
                    modifier = Modifier.padding(start = 13.dp, top = 4.dp)
                )
            }

            LibraryTabToggle(
                selected = subTab,
                onSelect = { subTab = it },
                modifier = Modifier
                    .padding(start = 108.dp, bottom = 6.dp)
                    .then(
                        if (introPlayed) Modifier
                        else Modifier.entranceReveal(rememberEntranceProgress(delayMillis = 60))
                    )
            )

            if (subTab == LibrarySubTab.STATS) {
                state.stats?.let { StatsView(it) }
            } else sections.forEachIndexed { index, section ->
                val revealDelay = 120 + index * 120

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 108.dp, end = 32.dp, top = 16.dp, bottom = 10.dp)
                        .then(
                            if (introPlayed) Modifier
                            else Modifier.entranceReveal(rememberEntranceProgress(delayMillis = revealDelay))
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SectionHeader(section.title)
                    Text(
                        text = "${section.entries.size} titles",
                        fontSize = 12.sp,
                        color = SeanimeOnSurfaceMuted
                    )
                }

                // No entrance reveal on card rows: composing dozens of cover
                // images while animating them caused visible jank on the first
                // library render — only the light text headers play the intro.
                // "Show All" opens a dedicated full-screen panel (ShowAllPanel)
                // rather than expanding in place — the old inline grid stranded
                // D-pad focus on the "Show Less" button.
                LibrarySectionRow(
                    entries = section.entries,
                    onCardClick = onCardClick,
                    onCardFocus = { featured = it },
                    onShowAll = { expandedSection = section.title }
                )
            }
        }

        // Full-screen "Show All" overlay for the chosen section.
        expandedSection?.let { title ->
            sections.find { it.title == title }?.let { section ->
                ShowAllPanel(
                    title = section.title,
                    entries = section.entries,
                    onCardClick = onCardClick,
                    onClose = { expandedSection = null }
                )
            }
        }
    }
}

@Composable
private fun LibrarySectionRow(
    entries: List<AnimeEntry>,
    onCardClick: (Int) -> Unit,
    onCardFocus: (AnimeEntry) -> Unit,
    onShowAll: () -> Unit
) {
    val displayed = entries.take(10)

    // NOTE: no focusRestorer here — restoring focus to a child that the lazy
    // row has already recycled targets a dead node and loses focus entirely.
    // Shared row style (full-width + contentPadding inset) so the fade dissolves
    // cards at the screen boundary, not on the first card.
    MediaCardRow(startInset = 108.dp, itemSpacing = 10.dp) {
        itemsIndexed(displayed) { _, entry ->
            AnimeCard(
                title = entry.title,
                coverImageUrl = entry.coverImageUrl,
                onClick = { onCardClick(entry.id) },
                onFocus = { onCardFocus(entry) },
                isAdult = entry.isAdult
            )
        }

        if (entries.size > 10) {
            item {
                ShowAllCard(total = entries.size, onClick = onShowAll)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Library — dedicated full-screen "Show All" panel for a section
// ---------------------------------------------------------------------------

/**
 * Full-screen grid of a library section's titles, opened from "Show All".
 * Replaces the old inline expand, which stranded D-pad focus on the "Show Less"
 * button and gave no sense of a distinct view. Here the [LazyVerticalGrid] is the
 * top-level scroller, so D-pad focus and scrolling work natively; the first card
 * is auto-focused and BACK closes the panel.
 */
@Composable
private fun ShowAllPanel(
    title: String,
    entries: List<AnimeEntry>,
    onCardClick: (Int) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    val firstCard = remember { FocusRequester() }
    val firstId = entries.firstOrNull()?.id
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SeanimeBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 28.dp)) {
            Row(
                modifier = Modifier.padding(start = 108.dp, end = 32.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SeriesPill(label = "‹ Back", onClick = onClose)
                PageTitle(title)
                Text("${entries.size} titles", fontSize = 12.sp, color = SeanimeOnSurfaceMuted)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(start = 108.dp, end = 32.dp, top = 4.dp, bottom = 48.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(entries, key = { it.id }) { entry ->
                    AnimeCard(
                        title = entry.title,
                        coverImageUrl = entry.coverImageUrl,
                        onClick = { onCardClick(entry.id) },
                        isAdult = entry.isAdult,
                        showCoverSpinner = true,
                        modifier = if (entry.id == firstId) Modifier.focusRequester(firstCard) else Modifier
                    )
                }
            }
        }
    }
    LaunchedEffect(title) { runCatching { firstCard.requestFocus() } }
}

// ---------------------------------------------------------------------------
// Library — Library | Stats sub-panel toggle (mirrors /extensions tab pills)
// ---------------------------------------------------------------------------

@Composable
private fun LibraryTabToggle(
    selected: LibrarySubTab,
    onSelect: (LibrarySubTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(4.dp)
    ) {
        LibrarySubTab.entries.forEach { tab ->
            LibraryPill(
                label = tab.label,
                selected = selected == tab,
                onClick = { onSelect(tab) }
            )
        }
    }
}

@Composable
private fun LibraryPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                when {
                    selected -> Color.White.copy(alpha = 0.14f)
                    isFocused -> Color.White.copy(alpha = 0.08f)
                    else -> Color.Transparent
                }
            )
            .border(
                width = 1.5.dp,
                color = if (isFocused) SeanimePrimary else Color.Transparent,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 22.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) SeanimeOnSurface else SeanimeOnSurfaceMuted
        )
    }
}

// ---------------------------------------------------------------------------
// Library — Stats sub-view (Denshi-style AniList stats overview, display-only)
// ---------------------------------------------------------------------------

// AniList/Denshi status palette.
private val StatusColors = mapOf(
    "Watching" to Color(0xFF68D391),
    "Completed" to Color(0xFF63B3ED),
    "Planning" to Color(0xFFB794F4),
    "Paused" to Color(0xFFF6AD55),
    "Dropped" to Color(0xFFFC8181)
)

@Composable
private fun StatsView(stats: LibraryStats, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 108.dp, end = 32.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OverviewTile("Total Anime", stats.totalAnime.toString(), Modifier.weight(1f))
            OverviewTile("Episodes Watched", stats.episodesWatched.toString(), Modifier.weight(1f))
            OverviewTile("Days Watched", "%.1f".format(stats.daysWatched), Modifier.weight(1f))
            OverviewTile(
                "Mean Score",
                if (stats.meanScore > 0) "%.1f".format(stats.meanScore) else "–",
                Modifier.weight(1f),
                accent = true
            )
        }

        if (stats.statusCounts.isNotEmpty()) {
            StatCard("Status Distribution") { StatusDistribution(stats.statusCounts) }
        }
        if (stats.scoreDistribution.any { it > 0 }) {
            StatCard("Score Distribution") { ScoreDistribution(stats.scoreDistribution) }
        }
        if (stats.topGenres.isNotEmpty()) {
            StatCard("Top Genres") { BarList(stats.topGenres) }
        }
        if (stats.formatCounts.isNotEmpty()) {
            StatCard("Formats") { BarList(stats.formatCounts.map { prettyFormat(it.first) to it.second }) }
        }
    }
}

@Composable
private fun OverviewTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false
) {
    // Focusable so D-pad can step onto it and drive the page's verticalScroll
    // (bringIntoView). Purely a scroll/highlight anchor — no click action.
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        modifier = modifier
            .clip(ExpressiveShapes.medium)
            .background(SeanimeSurface.copy(alpha = 0.7f))
            .border(
                if (focused) 1.5.dp else 1.dp,
                when {
                    focused -> SeanimePrimary
                    accent -> SeanimePrimary.copy(alpha = 0.5f)
                    else -> SeanimeBorder
                },
                ExpressiveShapes.medium
            )
            .focusable(interactionSource = interaction)
            .padding(vertical = 18.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            value,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (accent) SeanimePrimary else SeanimeOnSurface
        )
        Text(
            label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = SeanimeOnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatCard(title: String, content: @Composable () -> Unit) {
    // Focusable scroll anchor (see OverviewTile) — lets the D-pad reach and scroll
    // the lower stat cards, which are otherwise non-interactive.
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExpressiveShapes.medium)
            .background(SeanimeSurface.copy(alpha = 0.55f))
            .border(
                if (focused) 1.5.dp else 1.dp,
                if (focused) SeanimePrimary else SeanimeBorder,
                ExpressiveShapes.medium
            )
            .focusable(interactionSource = interaction)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SeanimeOnSurface)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusDistribution(counts: List<Pair<String, Int>>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
        ) {
            counts.forEach { (name, count) ->
                Box(
                    modifier = Modifier
                        .weight(count.toFloat())
                        .fillMaxHeight()
                        .background(StatusColors[name] ?: SeanimePrimary)
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            counts.forEach { (name, count) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(StatusColors[name] ?: SeanimePrimary)
                    )
                    Text(name, fontSize = 11.sp, color = SeanimeOnSurface)
                    Text("$count", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SeanimeOnSurfaceMuted)
                }
            }
        }
    }
}

@Composable
private fun ScoreDistribution(buckets: List<Int>) {
    val max = (buckets.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(
        modifier = Modifier.fillMaxWidth().height(84.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        buckets.forEachIndexed { i, count ->
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (count > 0) {
                    Text("$count", fontSize = 9.sp, color = SeanimeOnSurfaceMuted)
                    Spacer(Modifier.height(2.dp))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((4 + (count.toFloat() / max) * 52).dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (count > 0) SeanimePrimary else SeanimeBorder)
                )
                Spacer(Modifier.height(4.dp))
                Text("${i + 1}", fontSize = 9.sp, color = SeanimeOnSurfaceMuted)
            }
        }
    }
}

/** Horizontal ranked bars (label · bar · count) — AniList genre/format overview style. */
@Composable
private fun BarList(items: List<Pair<String, Int>>) {
    val max = (items.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        items.forEach { (label, count) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    label,
                    fontSize = 11.sp,
                    color = SeanimeOnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(120.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(count.toFloat() / max)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(SeanimePrimary.copy(alpha = 0.85f))
                    )
                }
                Text(
                    "$count",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SeanimeOnSurfaceMuted,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(30.dp)
                )
            }
        }
    }
}

/** Humanises an AniList format enum ("TV_SHORT" → "TV Short"), keeping acronyms. */
private fun prettyFormat(raw: String): String = when (raw) {
    "TV" -> "TV"
    "TV_SHORT" -> "TV Short"
    "MOVIE" -> "Movie"
    "SPECIAL" -> "Special"
    "OVA" -> "OVA"
    "ONA" -> "ONA"
    "MUSIC" -> "Music"
    else -> raw.replace("_", " ")
}

@Composable
private fun ShowAllCard(total: Int, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .width(150.dp)
            .height(225.dp + 30.dp)  // match AnimeCard total height
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isFocused) SeanimePrimary.copy(alpha = 0.18f) else SeanimeSurface,
                RoundedCornerShape(6.dp)
            )
            .border(
                1.dp,
                if (isFocused) SeanimePrimary else SeanimeBorder,
                RoundedCornerShape(6.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Show All",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isFocused) SeanimePrimary else SeanimeOnSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$total titles",
                fontSize = 11.sp,
                color = SeanimeOnSurfaceMuted
            )
        }
    }
}

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

// ---------------------------------------------------------------------------
// Preview stubs
// ---------------------------------------------------------------------------

private class PreviewFakeAniListRepository : AniListRepository {
    override suspend fun getViewer() = Result.failure<AniListUser>(UnsupportedOperationException())
    override suspend fun getMediaList(userId: Int, status: MediaListStatus) =
        Result.failure<List<AniListMediaListEntry>>(UnsupportedOperationException())
    override suspend fun getMediaById(id: Int) = Result.failure<AniListMedia>(UnsupportedOperationException())
    override suspend fun saveProgress(mediaId: Int, progress: Int, status: MediaListStatus) = Result.success(Unit)
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
fun HomeScreenPreview() {
    val fakeRepo = PreviewFakeAniListRepository()
    SeanimeTVTheme { HomeScreen(viewModel = HomeViewModel(fakeRepo), aniListRepository = fakeRepo) }
}
