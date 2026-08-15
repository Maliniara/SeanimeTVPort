package com.example.seanimetv.feature.home

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.example.seanimetv.components.AnimeCard
import com.example.seanimetv.components.ExpressiveLoadingIndicator
import com.example.seanimetv.components.LocalAnimeSettings
import com.example.seanimetv.components.PageTitle
import com.example.seanimetv.ui.theme.ExpressiveShapes
import com.example.seanimetv.components.entranceReveal
import com.example.seanimetv.components.rememberEntranceProgress
import com.example.seanimetv.data.anilist.model.AniListMedia
import com.example.seanimetv.data.anilist.repository.AniListRepository
import com.example.seanimetv.data.search.SearchHistoryStore
import com.example.seanimetv.ui.theme.SeanimeOnSurface
import com.example.seanimetv.ui.theme.SeanimeOnSurfaceMuted
import com.example.seanimetv.ui.theme.SeanimePrimary
import com.example.seanimetv.ui.theme.SeanimeSurface
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AniListSearchScreen(
    aniListRepository: AniListRepository,
    onCardClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AniListMedia>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val historyStore = remember { SearchHistoryStore(context) }
    val recentQueries by historyStore.history.collectAsState(initial = emptyList())

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            hasSearched = false
            return@LaunchedEffect
        }
        delay(500)
        isLoading = true
        results = aniListRepository.searchMedia(query).getOrElse { emptyList() }
        isLoading = false
        hasSearched = true
        // A phrase that produced results is worth remembering.
        if (results.isNotEmpty()) historyStore.record(query)
    }

    // Hide adult results from search unless the user enabled adult content.
    val anime = LocalAnimeSettings.current
    val visibleResults = if (anime.enableAdultContent) results else results.filterNot { it.isAdult }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        PageTitle(
            text = "Search",
            modifier = Modifier
                .padding(bottom = 16.dp)
                .entranceReveal(rememberEntranceProgress())
        )

        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = SeanimeOnSurface, fontSize = 16.sp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SeanimePrimary, ExpressiveShapes.medium)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search anime on AniList…",
                            fontSize = 16.sp,
                            color = SeanimeOnSurfaceMuted
                        )
                    }
                    innerTextField()
                }
            }
        )

        // Recent searches — shown while the box is empty; a chip re-runs the phrase.
        if (query.isBlank() && recentQueries.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Recent searches",
                fontSize = 12.sp,
                color = SeanimeOnSurfaceMuted,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                lazyRowItems(recentQueries, key = { it }) { phrase ->
                    SearchChip(text = phrase, onClick = { query = phrase })
                }
            }
        }

        // Title suggestions while typing — tap to complete the query.
        if (query.isNotBlank() && results.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val suggestions = results.mapNotNull { it.title?.english ?: it.title?.romaji }
                    .filterNot { it.equals(query, ignoreCase = true) }
                    .distinct()
                    .take(5)
                lazyRowItems(suggestions, key = { it }) { title ->
                    SearchChip(text = title, onClick = { query = title })
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SeanimePrimary)
            }
            hasSearched && visibleResults.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No results for “$query”",
                    color = SeanimeOnSurfaceMuted,
                    textAlign = TextAlign.Center
                )
            }
            visibleResults.isNotEmpty() -> LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 48.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(visibleResults, key = { it.id }) { media ->
                    AnimeCard(
                        title = media.title?.english ?: media.title?.romaji ?: "Unknown",
                        coverImageUrl = media.coverImage?.extraLarge ?: media.coverImage?.large ?: "",
                        onClick = { onCardClick(media.id) },
                        isAdult = media.isAdult
                    )
                }
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Find anime on AniList",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SeanimeOnSurface
                    )
                    Text(
                        text = "Type in the search box above to get started",
                        fontSize = 13.sp,
                        color = SeanimeOnSurfaceMuted
                    )
                }
            }
        }
    }
}

/** Focusable pill used for recent-search phrases and title suggestions. */
@Composable
private fun SearchChip(text: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.07f else 1f,
        animationSpec = tween(120),
        label = "searchChipScale"
    )
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(SeanimeSurface, RoundedCornerShape(16.dp))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) SeanimePrimary else SeanimeOnSurfaceMuted.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = SeanimeOnSurface,
            maxLines = 1
        )
    }
}
