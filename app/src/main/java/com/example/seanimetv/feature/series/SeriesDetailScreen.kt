package com.example.seanimetv.feature.series

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.seanimetv.components.ActionButton
import com.example.seanimetv.components.ExpressiveLoadingIndicator
import com.example.seanimetv.components.LocalIsLowRamDevice
import com.example.seanimetv.components.MediaCardRow
import com.example.seanimetv.components.SectionHeader
import com.example.seanimetv.data.anilist.model.AniListMedia
import com.example.seanimetv.data.anilist.model.AniListMediaListEntry
import com.example.seanimetv.data.anilist.model.AniListTitle
import com.example.seanimetv.data.anilist.model.AniListUser
import com.example.seanimetv.data.anilist.model.MediaListStatus
import com.example.seanimetv.data.anilist.repository.AniListRepository
import com.example.seanimetv.data.plugin.PluginEngine
import com.example.seanimetv.data.plugin.model.Episode
import com.example.seanimetv.data.plugin.model.EpisodeServer
import com.example.seanimetv.data.plugin.model.PluginEntry
import com.example.seanimetv.data.plugin.model.SearchResult
import com.example.seanimetv.ui.theme.ExpressiveShapes
import com.example.seanimetv.ui.theme.SeanimeBackground
import com.example.seanimetv.ui.theme.SeanimeOnSurfaceMuted
import com.example.seanimetv.ui.theme.SeanimePrimary
import com.example.seanimetv.ui.theme.SeanimeSurface
import com.example.seanimetv.ui.theme.SeanimeTVTheme
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private val BANNER_HEIGHT = 340.dp
private const val FADE_FRACTION = 0.60f
// The page background is ONE fixed full-screen scrim (see BannerBackdrop): it
// dissolves the banner into the solid page colour AND its whole profile darkens as
// the page scrolls. Crucially it is FIXED — no opaque panel "sheet" slides up over
// the banner, and that sliding sheet's edge was the moving dark band (both ways).
// SCRIM_FLOOR = the page colour below the hero (and everywhere once scrolled);
// SCRIM_HERO = the darkening behind the bottom-aligned hero text at rest.
private const val SCRIM_FLOOR = 0.94f
private const val SCRIM_HERO = 0.60f

/**
 * Anime entry page. A fixed cinematic banner sits behind a scrolling content
 * panel; the hero (cover + metadata + actions) floats on top and dissolves as
 * the panel rises. Below the fold: the Watch section, then Characters, Related
 * and Recommendations. The list-entry editor and the trailer play as overlays.
 *
 * This composable only wires ViewModel state to the sections and owns the
 * parallax/focus scaffold — each section lives in its own file.
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SeriesDetailScreen(
    viewModel: SeriesDetailViewModel,
    anilistId: Int,
    onBack: () -> Unit = {},
    onMediaClick: (Int) -> Unit = {},
    onNavigateToPlayer: (PlaybackRequest) -> Unit = {},
    autoResumeEpisode: Int = -1,
    modifier: Modifier = Modifier
) {
    val ui by viewModel.uiState.collectAsState()
    val resuming by viewModel.isResuming.collectAsState()
    val selectedServer by viewModel.selectedServer.collectAsState()
    val loadingServer by viewModel.isLoadingServer.collectAsState()
    val reloading by viewModel.isReloading.collectAsState()
    val dub by viewModel.isDub.collectAsState()
    val providers by viewModel.providers.collectAsState()
    val providerId by viewModel.selectedProviderId.collectAsState()
    val autoResolvedProviderId by viewModel.autoResolvedProviderId.collectAsState()
    val selectedEpisode by viewModel.selectedEpisode.collectAsState()
    val trailerOpen by viewModel.trailerVisible.collectAsState()
    val editOpen by viewModel.editVisible.collectAsState()
    val savingEntry by viewModel.isSavingEntry.collectAsState()
    val savedNotice by viewModel.savedNotice.collectAsState()

    LaunchedEffect(viewModel) { viewModel.navigationEvent.collect(onNavigateToPlayer) }
    LaunchedEffect(anilistId) {
        viewModel.setResumeTarget(autoResumeEpisode)
        viewModel.loadMedia(anilistId)
    }

    // Suppress the stretch/glow overscroll: it flashes a coloured edge that
    // fights the hero gradient when a row or the page bounces at its limit.
    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        Box(modifier.fillMaxSize().background(SeanimeBackground)) {
            when (val state = ui) {
                is SeriesDetailUiState.Loading -> CenteredSpinner()
                is SeriesDetailUiState.Error -> ErrorState(state.message) { viewModel.loadMedia(anilistId) }
                is SeriesDetailUiState.Success -> EntryContent(
                    state = state,
                    providers = providers,
                    providerId = providerId,
                    autoResolvedProviderId = autoResolvedProviderId,
                    selectedServer = selectedServer,
                    selectedEpisode = selectedEpisode,
                    dub = dub,
                    reloading = reloading,
                    loadingServer = loadingServer,
                    onMediaClick = onMediaClick,
                    viewModel = viewModel
                )
            }

            if (trailerOpen) {
                (ui as? SeriesDetailUiState.Success)?.media?.trailer?.id?.let { id ->
                    TrailerOverlay(trailerId = id, onClose = viewModel::hideTrailer)
                }
            }
            if (editOpen) {
                (ui as? SeriesDetailUiState.Success)?.media?.let { m ->
                    EditEntryOverlay(
                        media = m,
                        isSaving = savingEntry,
                        onSave = viewModel::saveEntry,
                        onDelete = viewModel::deleteEntry,
                        onClose = viewModel::hideEditDialog
                    )
                }
            }

            // Direct-resume: cover the screen with a spinner while the episode is
            // being resolved so the detail never flashes before the player opens.
            if (resuming) {
                Box(Modifier.fillMaxSize().background(SeanimeBackground), Alignment.Center) {
                    ExpressiveLoadingIndicator(size = 56.dp)
                }
            }

            // Edit save/delete confirmation — a small top-right pill (Denshi-style),
            // never a full-screen reload spinner.
            if (savingEntry || savedNotice != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 28.dp, end = 32.dp)
                        .background(SeanimeSurface.copy(alpha = 0.95f), ExpressiveShapes.full)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (savingEntry) {
                        ExpressiveLoadingIndicator(size = 16.dp)
                        Text("Saving…", color = SeanimeOnSurfaceMuted, fontSize = 13.sp)
                    } else {
                        Text("✓", color = SeanimePrimary, fontSize = 14.sp)
                        Text(savedNotice ?: "", color = SeanimeOnSurfaceMuted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredSpinner() =
    Box(Modifier.fillMaxSize(), Alignment.Center) { ExpressiveLoadingIndicator(size = 56.dp) }

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) =
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(message, color = SeanimeOnSurfaceMuted, textAlign = TextAlign.Center)
            ActionButton("Retry", onClick = onRetry)
        }
    }

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun EntryContent(
    state: SeriesDetailUiState.Success,
    providers: List<PluginEntry>,
    providerId: String?,
    autoResolvedProviderId: String?,
    selectedServer: String,
    selectedEpisode: Episode?,
    dub: Boolean,
    reloading: Boolean,
    loadingServer: Boolean,
    onMediaClick: (Int) -> Unit,
    viewModel: SeriesDetailViewModel
) {
    val media = state.media
    val watched = media.mediaListEntry?.progress ?: 0
    val episodes = state.episodes
    val characters = media.characters?.edges.orEmpty()
    val relations = media.relations?.edges.orEmpty()
    val recommendations = media.recommendations?.nodes?.mapNotNull { it.mediaRecommendation }.orEmpty()
    val hasPlugin = state.availableServers.isNotEmpty()
    val hasTrailer = media.trailer?.site == "youtube" && media.trailer?.id != null
    val bannerUrl = media.bannerImage ?: media.coverImage?.extraLarge ?: media.coverImage?.large

    val heroFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val fadeEndPx = with(LocalDensity.current) { BANNER_HEIGHT.toPx() * FADE_FRACTION }

    // Regaining a hero button (which sits outside the scroller) does not trigger
    // bring-into-view, so drive the scroll back to the top by hand.
    val onHeroFocused = Modifier.onFocusChanged { if (it.isFocused) scope.launch { scroll.animateScrollTo(0) } }

    // The banner is a fixed backdrop, so any panel strip left uncovered above
    // "Watch" reads as dead space. Measure the viewport top and the panel top
    // (both drift as the page scrolls); the panel is pinned when they align.
    var viewportTop by remember { mutableStateOf(0f) }
    var viewportHeightPx by remember { mutableStateOf(0) }
    var panelTop by remember { mutableStateOf(0f) }

    // Single source of truth for the panel scroll. While the whole Watch panel
    // (chips + preview + episode list) owns focus, animate to the pinned position
    // once, then HOLD it there by snapping back any drift the episode list's edge
    // bring-into-view introduces. This is what stops the page "running away" on
    // the first/last episode and keeps banner↔panel a single clean motion.
    var panelFocused by remember { mutableStateOf(false) }
    LaunchedEffect(panelFocused) {
        if (!panelFocused) return@LaunchedEffect
        val target = (scroll.value + (panelTop - viewportTop)).toInt().coerceAtLeast(0)
        if (kotlin.math.abs(scroll.value - target) > 2) scroll.animateScrollTo(target)
    }

    // The panel is pinned by hand (above). Tell the PAGE scroller to ignore the
    // framework's focus-driven bring-into-view while the panel owns focus — that
    // auto-scroll is exactly what made the whole panel jump-and-return on every
    // focus hop between chips / preview / carousel. Sections below the panel still
    // scroll into view normally, because panelFocused is false when they're focused.
    val defaultSpec = LocalBringIntoViewSpec.current
    val pinnedSpec = remember(defaultSpec) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                if (panelFocused) 0f
                else defaultSpec.calculateScrollDistance(offset, size, containerSize)
        }
    }

    // Entrance choreography: banner settles from a slight zoom while the hero
    // pieces rise in a stagger. Everything stays composed (graphicsLayer only) so
    // the Watch button can hold focus from the first frame.
    val lowRam = LocalIsLowRamDevice.current
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val bannerScale by animateFloatAsState(
        targetValue = if (entered || lowRam) 1.10f else 1.18f,
        animationSpec = if (lowRam) snap() else tween(1400, easing = FastOutSlowInEasing),
        label = "bannerScale"
    )
    val cover by stagger(entered, 0)
    val titleIn by stagger(entered, 120)
    val meta by stagger(entered, 240)
    val desc by stagger(entered, 340)
    val actions by stagger(entered, 440)

    Box(Modifier.fillMaxSize()) {

        // z=2 — hero on top of everything, fading + drifting up with the scroll.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BANNER_HEIGHT)
                .zIndex(2f)
                .graphicsLayer {
                    val y = scroll.value.toFloat()
                    alpha = (1f - y / fadeEndPx).coerceIn(0f, 1f)
                    translationY = -y * 0.5f
                }
        ) {
            SeriesHero(
                media = media,
                hasPlugin = hasPlugin,
                hasTrailer = hasTrailer,
                revealCover = cover,
                revealTitle = titleIn,
                revealMeta = meta,
                revealDesc = desc,
                revealButtons = actions,
                watchButtonModifier = Modifier.focusRequester(heroFocus)
                    .focusProperties { down = panelFocus }.then(onHeroFocused),
                // Order is Watch → Edit → Trailer; Edit is always present, so it is
                // the hero focus anchor whenever there is no Watch button.
                editButtonModifier = heroButtonModifier(isFirst = !hasPlugin, heroFocus, panelFocus, onHeroFocused),
                trailerButtonModifier = heroButtonModifier(isFirst = false, heroFocus, panelFocus, onHeroFocused),
                onWatch = { episodes.firstOrNull()?.let(viewModel::onEpisodeSelected) },
                onTrailer = viewModel::showTrailer,
                onEdit = viewModel::showEditDialog
            )
        }

        // z=0 — fixed banner backdrop + one continuous scrim.
        BannerBackdrop(bannerUrl = bannerUrl, scale = bannerScale, scrollY = { scroll.value.toFloat() }, fadeEndPx = fadeEndPx)

        // z=1 — the scroller. A tall spacer keeps the top see-through over the
        // banner; the panel below is 70% opaque so the banner still glows faintly.
        // pinnedSpec suppresses focus auto-scroll here while the panel is focused.
        CompositionLocalProvider(LocalBringIntoViewSpec provides pinnedSpec) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    viewportTop = it.positionInRoot().y
                    viewportHeightPx = it.size.height
                }
                .verticalScroll(scroll, enabled = !panelFocused)
                .zIndex(1f)
        ) {
            // Content is transparent and starts right under the hero; the fixed
            // full-screen scrim behind it (BannerBackdrop) does all the darkening.
            Spacer(Modifier.height(BANNER_HEIGHT))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { panelTop = it.positionInRoot().y }
                    // No background here: the fixed full-screen scrim (BannerBackdrop)
                    // provides the darkening, so nothing opaque slides over the banner.
                    .padding(top = 20.dp)
            ) {
                WatchPanel(
                    providers = providers,
                    selectedProviderId = providerId,
                    autoResolvedProviderName = providers.find { it.id == autoResolvedProviderId }?.name,
                    servers = state.availableServers,
                    selectedServer = selectedServer,
                    isDub = dub,
                    isReloading = reloading,
                    isLoadingServer = loadingServer,
                    episodes = episodes,
                    selectedEpisode = selectedEpisode,
                    watchedCount = watched,
                    fallbackImage = bannerUrl,
                    onProviderSelected = viewModel::onProviderSelected,
                    onServerSelected = viewModel::onServerSelected,
                    onToggleDub = viewModel::toggleDub,
                    onEpisodeFocused = viewModel::selectEpisode,
                    onEpisodePlay = { viewModel.selectEpisode(it); viewModel.onEpisodeSelected(it) },
                    onPanelFocusChanged = { panelFocused = it },
                    episodeListSpec = defaultSpec,
                    viewportHeightPx = viewportHeightPx.toFloat(),
                    firstFocusModifier = Modifier.focusRequester(panelFocus),
                    chipUpModifier = Modifier.focusProperties { up = heroFocus }
                )

                if (characters.isNotEmpty()) {
                    MediaRow("Characters", itemSpacing = 16.dp, bottomPadding = 28.dp) {
                        items(characters) { CharacterCard(charEdge = it) }
                    }
                }
                if (relations.isNotEmpty()) {
                    MediaRow("Related", itemSpacing = 12.dp, bottomPadding = 28.dp) {
                        items(relations) { edge -> RelatedCard(edge = edge, onClick = { edge.node?.id?.let(onMediaClick) }) }
                    }
                }
                if (recommendations.isNotEmpty()) {
                    MediaRow("Recommendations", itemSpacing = 12.dp, bottomPadding = 48.dp) {
                        items(recommendations) { rec -> RecommendationCard(media = rec, onClick = { onMediaClick(rec.id) }) }
                    }
                }
                Spacer(Modifier.height(64.dp))
            }
        }
        }
    }

    // Backstop for the rare case the TV framework parks initial focus on a scroll
    // item: pull it onto the hero button (which does not move the scroll).
    LaunchedEffect(Unit) { runCatching { heroFocus.requestFocus() } }
}

/** Down bridges to the panel; when [isFirst] this button is the hero focus anchor. */
private fun heroButtonModifier(
    isFirst: Boolean,
    heroFocus: FocusRequester,
    panelFocus: FocusRequester,
    onHeroFocused: Modifier
): Modifier = (if (isFirst) Modifier.focusRequester(heroFocus) else Modifier)
    .focusProperties { down = panelFocus }
    .then(onHeroFocused)

@Composable
private fun BannerBackdrop(bannerUrl: String?, scale: Float, scrollY: () -> Float, fadeEndPx: Float) {
    if (bannerUrl != null) {
        val context = LocalContext.current
        // The banner is darkened by the scrim and mostly covered by the opaque
        // panel, so decode it small (≈½ res) — big memory/fill saving, invisible.
        val request = remember(bannerUrl) {
            ImageRequest.Builder(context).data(bannerUrl).size(960, 540).build()
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
                .graphicsLayer {
                    translationY = -(scrollY() / fadeEndPx).coerceIn(0f, 1f) * 24.dp.toPx()
                    scaleX = scale
                    scaleY = scale
                }
        )
    } else {
        Box(Modifier.fillMaxSize().zIndex(0f).background(Color(0xFF0D1428)))
    }
    // ONE fixed full-screen scrim = the whole page background. It dissolves the
    // banner (transparent at the very top → the hero-text darkening → the solid page
    // colour below) and its ENTIRE profile darkens toward SCRIM_FLOOR as the page
    // scrolls (p). Fixed and drawn in the draw phase (drawBehind, no recomposition),
    // so it never slides relative to the scrolling content — hence no moving dark
    // band in either direction, and no hard edge that reads as a "cutoff".
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(0f)
            .drawBehind {
                val p = (scrollY() / fadeEndPx).coerceIn(0f, 1f)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to SeanimeBackground.copy(alpha = lerp(0f, SCRIM_FLOOR, p)),
                        0.14f to SeanimeBackground.copy(alpha = lerp(0.12f, SCRIM_FLOOR, p)),
                        0.30f to SeanimeBackground.copy(alpha = lerp(SCRIM_HERO, SCRIM_FLOOR, p)),
                        1f to SeanimeBackground.copy(alpha = SCRIM_FLOOR)
                    )
                )
            }
    )
}

@Composable
private fun MediaRow(title: String, itemSpacing: Dp, bottomPadding: Dp, items: LazyListScope.() -> Unit) {
    SectionHeader(title, modifier = Modifier.padding(horizontal = 32.dp))
    Spacer(Modifier.height(12.dp))
    // Shared row style so Characters / Related / Recommendations fade at the edges
    // exactly like Home, My Library and Discovery.
    MediaCardRow(
        modifier = Modifier.padding(bottom = bottomPadding),
        startInset = 32.dp,
        itemSpacing = itemSpacing,
        content = items
    )
}

/** One-shot staggered 0→1 driver for the hero entrance (snaps on low-RAM boxes). */
@Composable
private fun stagger(started: Boolean, delayMillis: Int): State<Float> {
    val lowRam = LocalIsLowRamDevice.current
    return animateFloatAsState(
        targetValue = if (started || lowRam) 1f else 0f,
        animationSpec = if (lowRam) snap() else tween(550, delayMillis = delayMillis, easing = FastOutSlowInEasing),
        label = "stagger$delayMillis"
    )
}

// ---------------------------------------------------------------------------
// @Preview scaffolding
// ---------------------------------------------------------------------------

private class PreviewRepo : AniListRepository {
    override suspend fun getViewer() = Result.failure<AniListUser>(UnsupportedOperationException())
    override suspend fun getMediaList(userId: Int, status: MediaListStatus) =
        Result.failure<List<AniListMediaListEntry>>(UnsupportedOperationException())
    override suspend fun getMediaById(id: Int) = Result.success(
        AniListMedia(
            id = 1, title = AniListTitle(romaji = "Sousou no Frieren", english = "Frieren: Beyond Journey's End"),
            episodes = 28, status = "FINISHED",
            description = "An elven mage reflects on her long life after the death of her companions.",
            genres = listOf("Adventure", "Drama", "Fantasy"), averageScore = 90
        )
    )
    override suspend fun saveProgress(mediaId: Int, progress: Int, status: MediaListStatus) = Result.success(Unit)
}

private class PreviewEngine : PluginEngine {
    override suspend fun search(query: String, media: AniListMedia?, dub: Boolean) = Result.success(emptyList<SearchResult>())
    override suspend fun findEpisodes(searchId: String) = Result.success(emptyList<Episode>())
    override suspend fun findEpisodeServer(episode: Episode, server: String) =
        Result.failure<EpisodeServer>(UnsupportedOperationException())
    override fun getAvailableServers(): List<String> = emptyList()
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
@OptIn(ExperimentalTvMaterial3Api::class)
fun SeriesDetailScreenPreview() {
    SeanimeTVTheme {
        SeriesDetailScreen(viewModel = SeriesDetailViewModel(PreviewRepo(), PreviewEngine()), anilistId = 1)
    }
}
