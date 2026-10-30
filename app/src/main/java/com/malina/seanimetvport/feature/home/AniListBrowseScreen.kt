package com.malina.seanimetvport.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.malina.seanimetvport.components.AmbientBackdrop
import com.malina.seanimetvport.components.PageTitle
import com.malina.seanimetvport.components.SectionHeader
import com.malina.seanimetvport.components.ExpressiveLoadingIndicator
import com.malina.seanimetvport.components.ExpressiveLoadingIndicator
import com.malina.seanimetvport.components.entranceReveal
import androidx.compose.ui.draw.blur
import com.malina.seanimetvport.components.LocalAnimeSettings
import com.malina.seanimetvport.components.MediaCardRow
import com.malina.seanimetvport.components.rememberEntranceProgress
import com.malina.seanimetvport.ui.theme.ExpressiveMotion
import com.malina.seanimetvport.ui.theme.ExpressiveShapes
import com.malina.seanimetvport.ui.theme.SeanimeBackground
import com.malina.seanimetvport.ui.theme.SeanimeOnSurfaceMuted
import com.malina.seanimetvport.data.anilist.model.AniListMedia
import com.malina.seanimetvport.data.anilist.repository.AniListRepository
import com.malina.seanimetvport.ui.theme.SeanimeOnSurface
import com.malina.seanimetvport.ui.theme.SeanimePrimary
import com.malina.seanimetvport.ui.theme.SeanimeSurface
import java.util.Calendar
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Category rows — the Seanime Denshi Discovery layout. Each row is one
// browseMedia query; season-based rows derive the current/previous AniList
// season from the wall clock.
// ---------------------------------------------------------------------------

private data class BrowseRowSpec(
    val title: String,
    /** When set, the row shows only this many cards + a "Show all" card. */
    val rowLimit: Int? = null,
    val fetch: suspend (AniListRepository) -> List<AniListMedia>
)

private fun seasonOf(month: Int): String = when (month) {
    in 1..3 -> "WINTER"
    in 4..6 -> "SPRING"
    in 7..9 -> "SUMMER"
    else -> "FALL"
}

private fun previousSeason(season: String, year: Int): Pair<String, Int> = when (season) {
    "WINTER" -> "FALL" to year - 1
    "SPRING" -> "WINTER" to year
    "SUMMER" -> "SPRING" to year
    else -> "SUMMER" to year
}

private fun browseRowSpecs(): List<BrowseRowSpec> {
    val cal = Calendar.getInstance()
    val year = cal.get(Calendar.YEAR)
    val season = seasonOf(cal.get(Calendar.MONTH) + 1)
    val (prevSeason, prevYear) = previousSeason(season, year)
    return listOf(
        BrowseRowSpec("Trending Right Now") { repo ->
            repo.browseMedia(sort = listOf("TRENDING_DESC")).getOrElse { emptyList() }
        },
        BrowseRowSpec("Top 100 Anime", rowLimit = 15) { repo ->
            // AniList all-time top rated. perPage is capped at 30, so pull the
            // first 4 pages in parallel to cover the top 100.
            coroutineScope {
                (1..4).map { p ->
                    async { repo.browseMedia(sort = listOf("SCORE_DESC"), page = p).getOrElse { emptyList() } }
                }.awaitAll().flatten().distinctBy { it.id }.take(100)
            }
        },
        BrowseRowSpec("Aired Recently") { repo ->
            repo.browseMedia(sort = listOf("UPDATED_AT_DESC"), status = "RELEASING")
                .getOrElse { emptyList() }
        },
        BrowseRowSpec("Top of the Season") { repo ->
            repo.browseMedia(sort = listOf("POPULARITY_DESC"), season = season, seasonYear = year)
                .getOrElse { emptyList() }
        },
        BrowseRowSpec("Best of Last Season") { repo ->
            repo.browseMedia(sort = listOf("SCORE_DESC"), season = prevSeason, seasonYear = prevYear)
                .getOrElse { emptyList() }
        },
        BrowseRowSpec("You Might Have Missed") { repo ->
            repo.browseMedia(
                sort = listOf("POPULARITY_DESC"), season = prevSeason, seasonYear = prevYear, page = 2
            ).getOrElse { emptyList() }
        },
        BrowseRowSpec("Coming Soon") { repo ->
            repo.browseMedia(sort = listOf("POPULARITY_DESC"), status = "NOT_YET_RELEASED")
                .getOrElse { emptyList() }
        },
        BrowseRowSpec("Trending Movies") { repo ->
            repo.browseMedia(sort = listOf("TRENDING_DESC"), format = "MOVIE")
                .getOrElse { emptyList() }
        }
    )
}

@Composable
fun AniListBrowseScreen(
    aniListRepository: AniListRepository,
    onCardClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val specs = remember { browseRowSpecs() }
    var rowData by remember { mutableStateOf<Map<String, List<AniListMedia>>>(emptyMap()) }
    var loadedCount by remember { mutableStateOf(0) }
    var featured by remember { mutableStateOf<AniListMedia?>(null) }
    // The full list currently opened in the "Show all" panel (title + items).
    var showAll by remember { mutableStateOf<Pair<String, List<AniListMedia>>?>(null) }

    // All rows fetch in parallel and render as they arrive; the first result
    // also seeds the hero backdrop.
    LaunchedEffect(Unit) {
        specs.forEach { spec ->
            launch {
                val items = spec.fetch(aniListRepository)
                rowData = rowData + (spec.title to items)
                loadedCount++
                if (featured == null) featured = items.firstOrNull()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AmbientBackdrop(
            imageUrl = featured?.bannerImage
                ?: featured?.coverImage?.extraLarge
                ?: featured?.coverImage?.large
        )

        // Column+verticalScroll (not LazyColumn) — same as Home: the built-in
        // focus bring-into-view scrolls smoothly and never bounces rows.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 24.dp)
        ) {
            PageTitle(
                text = "Discovery",
                modifier = Modifier
                    .padding(start = 108.dp, end = 32.dp, bottom = 8.dp)
                    .entranceReveal(rememberEntranceProgress())
            )

            if (rowData.isEmpty() && loadedCount < specs.size) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SeanimePrimary)
                }
            }

            specs.forEachIndexed { index, spec ->
                val items = rowData[spec.title].orEmpty()
                if (items.isNotEmpty()) {
                    Box(
                        Modifier.entranceReveal(
                            rememberEntranceProgress(delayMillis = 100 + index * 60)
                        )
                    ) {
                        BrowseRow(
                            title = spec.title,
                            items = items,
                            rowLimit = spec.rowLimit,
                            onShowAll = { showAll = spec.title to items },
                            onCardClick = onCardClick,
                            onCardFocus = { featured = it }
                        )
                    }
                }
            }

            if (loadedCount == specs.size && rowData.values.all { it.isEmpty() }) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Couldn't load Discovery — check your connection",
                        color = SeanimeOnSurface.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(Modifier.height(48.dp))
        }

        // Full-screen "Show all" panel for a capped row (e.g. Top 100).
        showAll?.let { (title, list) ->
            DiscoveryShowAllPanel(
                title = title,
                items = list,
                onCardClick = onCardClick,
                onClose = { showAll = null }
            )
        }
    }
}

@Composable
private fun BrowseRow(
    title: String,
    items: List<AniListMedia>,
    rowLimit: Int?,
    onShowAll: () -> Unit,
    onCardClick: (Int) -> Unit,
    onCardFocus: (AniListMedia) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        SectionHeader(title, modifier = Modifier.padding(start = 108.dp, end = 32.dp))
        Spacer(Modifier.height(8.dp))
        // Shared My-Library row style: full-width row, inset via contentPadding so
        // the fade sits over the gutter and the first card stays crisp.
        val anime = LocalAnimeSettings.current
        val visible = if (anime.enableAdultContent) items else items.filterNot { it.isAdult }
        val capped = if (rowLimit != null) visible.take(rowLimit) else visible
        val hasMore = rowLimit != null && visible.size > rowLimit
        MediaCardRow(startInset = 108.dp, itemSpacing = 12.dp) {
            items(capped, key = { it.id }) { media ->
                Box(Modifier.width(148.dp)) {
                    BrowseCard(
                        media = media,
                        onClick = { onCardClick(media.id) },
                        onFocus = { onCardFocus(media) }
                    )
                }
            }
            if (hasMore) {
                item(key = "__show_all__") {
                    Box(Modifier.width(148.dp)) {
                        BrowseShowAllCard(remaining = visible.size - rowLimit!!, onClick = onShowAll)
                    }
                }
            }
        }
    }
}

/** Trailing card that opens the full list of a capped row (e.g. Top 100). */
@Composable
private fun BrowseShowAllCard(remaining: Int, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = ExpressiveMotion.spatialFast(),
        label = "showAllScale"
    )
    Column(
        modifier = Modifier
            .drawWithContent {
                val s = scale
                scale(scale = s, pivot = center) { this@drawWithContent.drawContent() }
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(ExpressiveShapes.medium)
                .background(SeanimeSurface)
                .border(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) SeanimePrimary else Color.Transparent,
                    shape = ExpressiveShapes.medium
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("＋", fontSize = 34.sp, color = if (focused) SeanimePrimary else SeanimeOnSurface)
                Text("Show all", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SeanimeOnSurface)
                Text("+$remaining more", fontSize = 11.sp, color = SeanimeOnSurfaceMuted)
            }
        }
        Text(
            text = "Full ranking",
            fontSize = 12.sp,
            color = SeanimeOnSurface.copy(alpha = 0.75f),
            minLines = 2,
            maxLines = 2,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
        )
    }
}

/** Full-screen grid of an entire row's titles (opened from a "Show all" card). */
@Composable
private fun DiscoveryShowAllPanel(
    title: String,
    items: List<AniListMedia>,
    onCardClick: (Int) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    val anime = LocalAnimeSettings.current
    val visible = if (anime.enableAdultContent) items else items.filterNot { it.isAdult }
    val firstFocus = remember { FocusRequester() }
    val firstId = visible.firstOrNull()?.id
    Box(modifier = Modifier.fillMaxSize().background(SeanimeBackground)) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 28.dp)) {
            Row(
                modifier = Modifier.padding(start = 108.dp, end = 32.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PageTitle(title)
                Text(
                    "${visible.size} titles · BACK to close",
                    fontSize = 12.sp,
                    color = SeanimeOnSurfaceMuted
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(start = 108.dp, end = 32.dp, top = 4.dp, bottom = 48.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                gridItems(visible, key = { it.id }) { media ->
                    Box(
                        modifier = if (media.id == firstId) Modifier.focusRequester(firstFocus) else Modifier
                    ) {
                        BrowseCard(media = media, onClick = { onCardClick(media.id) })
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { if (visible.isNotEmpty()) runCatching { firstFocus.requestFocus() } }
}

@Composable
private fun BrowseCard(
    media: AniListMedia,
    onClick: () -> Unit,
    onFocus: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val title = media.title?.english ?: media.title?.romaji ?: "Unknown"
    val anime = LocalAnimeSettings.current

    LaunchedEffect(isFocused) {
        if (isFocused) {
            // Debounced: hero/backdrop updates only after the user settles —
            // 300ms matches the backdrop's own debounce so a vertical row
            // change never recomposes the hero mid-scroll (frame-drop fix).
            delay(300)
            onFocus()
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = ExpressiveMotion.spatialFast(),
        label = "browseCardScale"
    )

    Column(
        modifier = Modifier
            .drawWithContent {
                val s = scale
                scale(scale = s, pivot = center) {
                    this@drawWithContent.drawContent()
                }
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
    ) {
        // Same card language as AnimeCard: 2:3 portrait, 8dp radius, primary
        // border + scale on focus
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(ExpressiveShapes.medium)
                .background(SeanimeSurface)
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
                    color = if (isFocused) SeanimePrimary else Color.Transparent,
                    shape = ExpressiveShapes.medium
                )
        ) {
            val imageUrl = media.coverImage?.extraLarge ?: media.coverImage?.large
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (media.isAdult && anime.blurAdultContent) Modifier.blur(18.dp) else Modifier)
                )
            }
            if (!anime.hideAudienceScore) media.averageScore?.let { score ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text("★ $score", fontSize = 10.sp, color = Color(0xFFFFD700))
                }
            }
        }

        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isFocused) SeanimeOnSurface else SeanimeOnSurface.copy(alpha = 0.75f),
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
        )
    }
}
