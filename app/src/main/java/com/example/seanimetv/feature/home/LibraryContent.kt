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

// ---------------------------------------------------------------------------
// Library — sections with LazyRow + Show All grid
// ---------------------------------------------------------------------------

private data class LibrarySection(val title: String, val entries: List<AnimeEntry>)

/** Sub-panels of the My Library screen, toggled by the pill switch (mirrors /extensions). */
private enum class LibrarySubTab(val label: String) { LISTS("Library"), STATS("Stats") }

@Composable
internal fun LibraryContent(
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

