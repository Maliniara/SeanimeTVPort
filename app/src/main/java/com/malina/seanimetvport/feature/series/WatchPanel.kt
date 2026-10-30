package com.malina.seanimetvport.feature.series

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.malina.seanimetvport.components.ExpressiveLoadingIndicator
import com.malina.seanimetvport.components.FocusSurface
import com.malina.seanimetvport.components.SectionHeader
import com.malina.seanimetvport.components.SeriesPill
import com.malina.seanimetvport.data.plugin.model.Episode
import com.malina.seanimetvport.data.plugin.model.PluginEntry
import com.malina.seanimetvport.ui.theme.ExpressiveShapes
import com.malina.seanimetvport.ui.theme.SeanimeOnSurfaceMuted
import com.malina.seanimetvport.ui.theme.SeanimeSurface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private val PANEL_INSET = 32.dp
internal val PREVIEW_HEIGHT = 210.dp
// Denshi-style episode list: the segment (range) size above which the list is
// paginated into "1 – 100 / 101 – 200 …" chips so long-runners stay navigable.
private const val EPISODES_PER_SEGMENT = 100
// The episode grid is a fixed-height viewport that scrolls INTERNALLY (never the
// frozen page). Cells size themselves to this minimum width; the viewport height
// is measured to fill whatever space is left under the preview (see WatchPanel).
private val GRID_CARD_MIN_WIDTH = 172.dp
private val GRID_BOTTOM_MARGIN = 24.dp
private val GRID_MIN_HEIGHT = 150.dp
// Fixed card width for the horizontal "Row" mode (unwatched strip).
private val ROW_CARD_WIDTH = 208.dp

/** Episode-list presentation: a horizontal strip of unwatched episodes, or the full grid. */
private enum class EpisodeMode { ROW, LIST }

/**
 * Seanime-style Watch section. Top: provider / server / sub-dub chips. Middle: a
 * wide preview of the selected episode. Bottom: the Denshi-style episode list — a
 * grid of episode cards that, for long-runners, is paginated behind "1 – 100 …"
 * range chips so only one segment is on screen at a time.
 *
 * The grid is a FIXED-height viewport that scrolls internally (its own
 * [episodeListSpec] restores bring-into-view) so it never asks the page to move
 * while the panel holds focus and the page is pinned — the same reason the
 * provider chips and preview sit above it, outside the scroll region.
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun WatchPanel(
    providers: List<PluginEntry>,
    selectedProviderId: String?,
    autoResolvedProviderName: String? = null,
    servers: List<String>,
    selectedServer: String,
    isDub: Boolean,
    isReloading: Boolean,
    isLoadingServer: Boolean,
    episodes: List<Episode>,
    selectedEpisode: Episode?,
    watchedCount: Int,
    fallbackImage: String?,
    onProviderSelected: (String) -> Unit,
    onServerSelected: (String) -> Unit,
    onToggleDub: () -> Unit,
    onEpisodeFocused: (Episode) -> Unit,
    onEpisodePlay: (Episode) -> Unit,
    onPanelFocusChanged: (Boolean) -> Unit,
    episodeListSpec: BringIntoViewSpec,
    viewportHeightPx: Float,
    firstFocusModifier: Modifier,
    chipUpModifier: Modifier,
    modifier: Modifier = Modifier
) {
    var openDropdown by remember { mutableStateOf<String?>(null) }
    val previewEpisode = selectedEpisode ?: episodes.firstOrNull()
    var panelTopPx by remember { mutableStateOf(0f) }

    Column(modifier = modifier
        .padding(bottom = 28.dp)
        .onGloballyPositioned { panelTopPx = it.positionInRoot().y }
        .onFocusChanged { onPanelFocusChanged(it.hasFocus) }) {

        SectionHeader("Watch", modifier = Modifier.padding(horizontal = PANEL_INSET))
        Spacer(Modifier.height(10.dp))

        // ── Provider ⌄ · server ⌄ · sub/dub ──────────────────────────────
        Row(
            modifier = Modifier.padding(horizontal = PANEL_INSET),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isAutoProvider = selectedProviderId == SeriesDetailViewModel.AUTO_PROVIDER_ID
            val providerName = when {
                isAutoProvider -> "Auto" + (autoResolvedProviderName?.let { " · $it" } ?: "")
                else -> providers.find { it.id == selectedProviderId }?.name
                    ?: selectedProviderId ?: "Provider"
            }
            Box {
                SelectorChip(
                    label = "⌂  $providerName  ⌄",
                    onClick = { openDropdown = if (openDropdown == "provider") null else "provider" },
                    modifier = firstFocusModifier.then(chipUpModifier)
                )
                SelectionPopup(
                    expanded = openDropdown == "provider",
                    title = "Provider",
                    options = listOf(SeriesDetailViewModel.AUTO_PROVIDER_ID to "Auto") +
                        providers.map { it.id to it.name },
                    selectedId = selectedProviderId,
                    onSelect = { id -> openDropdown = null; onProviderSelected(id) },
                    onClose = { openDropdown = null }
                )
            }
            if (servers.isNotEmpty()) {
                Box {
                    SelectorChip(
                        label = "$selectedServer  ⌄",
                        onClick = { openDropdown = if (openDropdown == "server") null else "server" },
                        modifier = chipUpModifier
                    )
                    SelectionPopup(
                        expanded = openDropdown == "server",
                        title = "Server",
                        options = servers.map { it to it },
                        selectedId = selectedServer,
                        onSelect = { id -> openDropdown = null; onServerSelected(id) },
                        onClose = { openDropdown = null }
                    )
                }
            }
            if (isReloading || isLoadingServer) {
                ExpressiveLoadingIndicator(
                    modifier = Modifier.padding(start = 4.dp),
                    size = 18.dp
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        if (episodes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PREVIEW_HEIGHT)
                    .padding(horizontal = PANEL_INSET)
                    .clip(ExpressiveShapes.medium)
                    .background(SeanimeSurface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No episodes from this provider.\nTry another provider or install one from Extensions → Marketplace.",
                    fontSize = 12.sp,
                    color = SeanimeOnSurfaceMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                )
            }
        } else {
            // ── Selected-episode preview ─────────────────────────────────
            EpisodePreview(
                episode = previewEpisode,
                fallbackImage = fallbackImage,
                isLoading = isLoadingServer,
                onPlay = { previewEpisode?.let(onEpisodePlay) },
                modifier = Modifier.padding(horizontal = PANEL_INSET)
            )
            Spacer(Modifier.height(16.dp))

            // ── Row / List mode toggle ───────────────────────────────────
            var episodeMode by remember { mutableStateOf(EpisodeMode.ROW) }
            Row(
                modifier = Modifier.padding(horizontal = PANEL_INSET),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SeriesPill(
                    label = "Row",
                    onClick = { episodeMode = EpisodeMode.ROW },
                    selected = episodeMode == EpisodeMode.ROW
                )
                SeriesPill(
                    label = "List",
                    onClick = { episodeMode = EpisodeMode.LIST },
                    selected = episodeMode == EpisodeMode.LIST
                )
            }
            Spacer(Modifier.height(12.dp))

            when (episodeMode) {
                // Row: the full episode strip (watched episodes stay visible) that
                // auto-scrolls to the episode the user is on — the selected one, else
                // the first unwatched.
                EpisodeMode.ROW -> {
                    val rowState = rememberLazyListState()
                    // Position the row ONCE per episode list. Keying this on the
                    // selected episode would re-fire on every D-pad move (each card's
                    // onFocus updates it), snapping the focused card to the left edge
                    // and fighting the LazyRow's own bring-into-view.
                    LaunchedEffect(episodes) {
                        val sel = episodes.indexOfFirst { it.id == selectedEpisode?.id }
                        val target = if (sel >= 0) {
                            sel
                        } else {
                            watchedCount.coerceIn(0, (episodes.size - 1).coerceAtLeast(0))
                        }
                        if (target > 0) runCatching { rowState.scrollToItem(target) }
                    }
                    CompositionLocalProvider(LocalBringIntoViewSpec provides episodeListSpec) {
                        LazyRow(
                            state = rowState,
                            // Vertical padding = headroom so the focused card's scale
                            // isn't clipped by the row's bounds.
                            contentPadding = PaddingValues(horizontal = PANEL_INSET, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(episodes.size, key = { episodes[it].id }) { i ->
                                val episode = episodes[i]
                                Box(Modifier.width(ROW_CARD_WIDTH)) {
                                    EpisodeCard(
                                        episode = episode,
                                        isSelected = episode.id == previewEpisode?.id,
                                        isWatched = watchedCount > 0 && episode.number <= watchedCount,
                                        onFocus = { onEpisodeFocused(episode) },
                                        onClick = { onEpisodePlay(episode) }
                                    )
                                }
                            }
                        }
                    }
                }
                // List: the full Denshi-style grid (segmented + internally scrolled).
                EpisodeMode.LIST -> {
                    val segmented = episodes.size > EPISODES_PER_SEGMENT
                    val segmentCount = (episodes.size + EPISODES_PER_SEGMENT - 1) / EPISODES_PER_SEGMENT
                    // Start on the segment holding the selected episode; re-init on reload.
                    var selectedSegment by remember(episodes) {
                        val selIdx = episodes.indexOfFirst { it.id == previewEpisode?.id }.coerceAtLeast(0)
                        mutableStateOf(selIdx / EPISODES_PER_SEGMENT)
                    }
                    val segStart = selectedSegment * EPISODES_PER_SEGMENT
                    val segEnd = minOf(segStart + EPISODES_PER_SEGMENT, episodes.size)
                    val visibleEpisodes = episodes.subList(segStart, segEnd)

                    // Grid viewport height = the space left under everything above it, so
                    // it always fits inside the pinned panel regardless of screen density.
                    // (gridTopPx − panelTopPx) is the fixed height of the header/chips/
                    // preview/toggle/range chips above — scroll-independent, so no jump.
                    val density = LocalDensity.current
                    var gridTopPx by remember { mutableStateOf(0f) }
                    val abovePx = (gridTopPx - panelTopPx).coerceAtLeast(0f)
                    val minGridPx = with(density) { GRID_MIN_HEIGHT.toPx() }
                    val marginPx = with(density) { GRID_BOTTOM_MARGIN.toPx() }
                    val gridHeight = with(density) {
                        (viewportHeightPx - abovePx - marginPx).coerceAtLeast(minGridPx).toDp()
                    }

                    val gridState = rememberLazyGridState()
                    LaunchedEffect(selectedSegment, episodes) {
                        val idx = visibleEpisodes.indexOfFirst { it.id == previewEpisode?.id }
                        if (idx > 0) runCatching { gridState.scrollToItem(idx) }
                    }

                    // The page scroller suppresses focus auto-scroll while the panel is
                    // focused (see SeriesDetailScreen). Restore the DEFAULT spec here so the
                    // grid (and range chips) scroll THEMSELVES to the focused element.
                    CompositionLocalProvider(LocalBringIntoViewSpec provides episodeListSpec) {
                        Column {
                            if (segmented) {
                                RangeSelector(
                                    segmentCount = segmentCount,
                                    selectedSegment = selectedSegment,
                                    episodeCount = episodes.size,
                                    onSelect = { selectedSegment = it }
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Adaptive(minSize = GRID_CARD_MIN_WIDTH),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(gridHeight)
                                    .onGloballyPositioned { gridTopPx = it.positionInRoot().y },
                                contentPadding = PaddingValues(horizontal = PANEL_INSET),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                gridItems(visibleEpisodes, key = { it.id }) { episode ->
                                    EpisodeCard(
                                        episode = episode,
                                        isSelected = episode.id == previewEpisode?.id,
                                        isWatched = watchedCount > 0 && episode.number <= watchedCount,
                                        onFocus = { onEpisodeFocused(episode) },
                                        onClick = { onEpisodePlay(episode) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Horizontal range picker shown above the episode grid for long-runners. Each
 * chip jumps the grid to a 100-episode segment ("1 – 100", "101 – 200", …). It is
 * a [LazyRow] so it scrolls sideways for series with many segments, and the active
 * range is highlighted via [FocusSurface]'s selected state.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RangeSelector(
    segmentCount: Int,
    selectedSegment: Int,
    episodeCount: Int,
    onSelect: (Int) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = PANEL_INSET),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(segmentCount) { seg ->
            val start = seg * EPISODES_PER_SEGMENT + 1
            val end = minOf((seg + 1) * EPISODES_PER_SEGMENT, episodeCount)
            SeriesPill(
                label = "$start – $end",
                selected = seg == selectedSegment,
                onClick = { onSelect(seg) }
            )
        }
    }
}

