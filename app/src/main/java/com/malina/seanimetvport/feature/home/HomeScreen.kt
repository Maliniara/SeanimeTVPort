package com.malina.seanimetvport.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.malina.seanimetvport.components.ActionButton
import com.malina.seanimetvport.components.ExpressiveLoadingIndicator
import com.malina.seanimetvport.components.LibraryTab
import com.malina.seanimetvport.components.NavSection
import com.malina.seanimetvport.components.SideNavigation
import com.malina.seanimetvport.data.anilist.model.AniListMedia
import com.malina.seanimetvport.data.anilist.model.AniListMediaListEntry
import com.malina.seanimetvport.data.anilist.model.AniListUser
import com.malina.seanimetvport.data.anilist.model.MediaListStatus
import com.malina.seanimetvport.data.anilist.repository.AniListRepository
import com.malina.seanimetvport.data.metadata.AniZipClient
import com.malina.seanimetvport.ui.theme.SeanimeBackground
import com.malina.seanimetvport.ui.theme.SeanimeOnSurfaceMuted
import com.malina.seanimetvport.ui.theme.SeanimeTVTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
