package com.malina.seanimetvport.feature.series

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.malina.seanimetvport.components.ExpressiveLoadingIndicator
import com.malina.seanimetvport.components.FocusSurface
import com.malina.seanimetvport.components.LocalAnimeSettings
import com.malina.seanimetvport.data.plugin.model.Episode
import com.malina.seanimetvport.ui.theme.ExpressiveMotion
import com.malina.seanimetvport.ui.theme.ExpressiveShapes
import com.malina.seanimetvport.ui.theme.SeanimeOnSurface
import com.malina.seanimetvport.ui.theme.SeanimeOnSurfaceMuted
import com.malina.seanimetvport.ui.theme.SeanimePrimary
import com.malina.seanimetvport.ui.theme.SeanimeSurface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun EpisodePreview(
    episode: Episode?,
    fallbackImage: String?,
    isLoading: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    FocusSurface(
        onClick = onPlay,
        modifier = modifier.fillMaxWidth().height(PREVIEW_HEIGHT),
        shape = ExpressiveShapes.medium,
        containerColor = Color.Black,
        focusedContainerColor = Color.Black,
        focusedBorderColor = SeanimePrimary,
        unfocusedBorderColor = Color.White.copy(alpha = 0.10f),
        borderWidth = 2.dp
    ) { focused ->
        val image = episode?.thumbnail ?: fallbackImage
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = if (focused) 0.15f else 0.35f),
                    1f to Color.Black.copy(alpha = 0.85f)
                )
            )
        )
        // Play affordance
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(66.dp)
                .clip(CircleShape)
                .background(if (focused) SeanimePrimary else Color.Black.copy(alpha = 0.55f))
                .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                ExpressiveLoadingIndicator(size = 28.dp, color = Color.White)
            } else {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        // Caption
        if (episode != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text("Episode ${episode.number}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SeanimePrimary)
                episode.title?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                episode.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun EpisodeCard(
    episode: Episode,
    isSelected: Boolean,
    isWatched: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    // Spoiler-safe art: for episodes the user hasn't watched yet, hide the
    // thumbnail (leaving the plain surface) and the episode title.
    val spoilerSafe = LocalAnimeSettings.current.hideSpoilers && !isWatched
    // Focus scale via graphicsLayer (draw-only, no layout change → no bring-into-view
    // fight) — the card springs up when focused. Lives inside a LazyRow/LazyGrid,
    // which handle focus scrolling, so the scale can't run the page away.
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.10f else 1f,
        animationSpec = ExpressiveMotion.spatialFast(),
        label = "episodeCardScale"
    )
    FocusSurface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { isFocused = it.isFocused; if (it.isFocused) onFocus() },
        // Rectangular so the bottom labels aren't clipped by rounded corners — the
        // visible rounding comes from the inner thumbnail's own clip.
        shape = RectangleShape,
        focusedContainerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
        selected = isSelected,
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent
    ) { focused ->
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(ExpressiveShapes.medium)
                    .background(SeanimeSurface)
                    .border(
                        width = if (focused) 2.dp else if (isSelected) 1.5.dp else 0.dp,
                        color = if (focused) SeanimePrimary else SeanimePrimary.copy(alpha = 0.45f),
                        shape = ExpressiveShapes.medium
                    )
            ) {
                if (!spoilerSafe) episode.thumbnail?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (isWatched) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(SeanimePrimary)
                    )
                }
                // Play affordance on focus — same centered vector glyph as the
                // player's transport button (a text "▶" sat visually off-centre).
                if (focused) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(SeanimePrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Episode ${episode.number}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    focused || isSelected -> SeanimePrimary
                    isWatched -> SeanimeOnSurfaceMuted
                    else -> SeanimeOnSurface
                },
                maxLines = 1,
                modifier = Modifier.padding(start = 2.dp)
            )
            Text(
                text = when {
                    spoilerSafe -> "Hidden (spoiler-safe)"
                    else -> episode.title?.takeIf { it.isNotBlank() } ?: "~24m"
                },
                fontSize = 11.sp,
                color = if (focused) SeanimeOnSurface else SeanimeOnSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp, top = 1.dp)
            )
        }
    }
}
