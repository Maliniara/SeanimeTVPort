package com.example.seanimetv.feature.series

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text as StableText
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.seanimetv.data.anilist.model.AniListCharacterEdge
import com.example.seanimetv.data.anilist.model.AniListMedia
import com.example.seanimetv.data.anilist.model.AniListRelationEdge
import com.example.seanimetv.components.FocusSurface
import com.example.seanimetv.ui.theme.SeanimeOnSurface
import com.example.seanimetv.ui.theme.SeanimeOnSurfaceMuted
import com.example.seanimetv.ui.theme.SeanimePrimary
import com.example.seanimetv.ui.theme.SeanimeSurface

// ---------------------------------------------------------------------------
// Character card — circular avatar, non-navigating (focusable for D-pad only)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun CharacterCard(charEdge: AniListCharacterEdge, modifier: Modifier = Modifier) {
    val char = charEdge.node ?: return
    val name = char.name?.full ?: return

    FocusSurface(
        onClick = {},
        modifier = modifier.width(80.dp),
        shape = RoundedCornerShape(8.dp),
        focusedContainerColor = Color.Transparent,
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        focusedScale = 1.06f
    ) { focused ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(SeanimeSurface)
                    .border(
                        width = if (focused) 2.dp else 0.dp,
                        color = SeanimePrimary,
                        shape = CircleShape
                    )
            ) {
                char.image?.medium?.let { imgUrl ->
                    AsyncImage(
                        model = imgUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            StableText(
                name,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = SeanimeOnSurface.copy(alpha = 0.85f),
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 13.sp,
                textAlign = TextAlign.Center
            )
            charEdge.role?.takeIf { it.isNotBlank() }?.let { role ->
                Text(
                    role.lowercase().replaceFirstChar { it.uppercase() },
                    fontSize = 9.sp,
                    color = SeanimePrimary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Related + Recommendation share the same poster-card body (DRY)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun RelatedCard(edge: AniListRelationEdge, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val media = edge.node ?: return
    val title = media.title?.english ?: media.title?.romaji ?: return
    val relType = edge.relationType?.lowercase()?.replace("_", " ")?.replaceFirstChar { it.uppercase() }

    PosterCard(
        coverUrl = media.coverImage?.large,
        title = title,
        onClick = onClick,
        width = 120.dp,
        modifier = modifier,
        topStartBadge = relType?.let { { PosterBadge(it, Color.Black.copy(alpha = 0.72f), Color.White) } }
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun RecommendationCard(media: AniListMedia, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val title = media.title?.english ?: media.title?.romaji ?: return
    val year = media.startDate?.year ?: media.seasonYear
    val score = media.averageScore

    PosterCard(
        coverUrl = media.coverImage?.large,
        title = title,
        onClick = onClick,
        width = 130.dp,
        modifier = modifier,
        footer = year?.let { "$it" },
        topEndBadge = score?.let { { PosterBadge("★ $it", Color(0xFF1A3A2A), Color(0xFF52B788)) } }
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PosterCard(
    coverUrl: String?,
    title: String,
    onClick: () -> Unit,
    width: Dp,
    modifier: Modifier = Modifier,
    footer: String? = null,
    topStartBadge: (@Composable () -> Unit)? = null,
    topEndBadge: (@Composable () -> Unit)? = null
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.width(width),
        shape = RoundedCornerShape(6.dp),
        focusedContainerColor = Color.Transparent,
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        focusedScale = 1.06f
    ) { focused ->
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SeanimeSurface)
                    .border(
                        width = if (focused) 2.dp else 0.dp,
                        color = SeanimePrimary,
                        shape = RoundedCornerShape(6.dp)
                    )
            ) {
                coverUrl?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                topStartBadge?.let {
                    Box(Modifier.align(Alignment.TopStart).padding(4.dp)) { it() }
                }
                topEndBadge?.let {
                    Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) { it() }
                }
            }
            StableText(
                title,
                fontSize = 10.sp,
                color = if (focused) SeanimeOnSurface else SeanimeOnSurface.copy(alpha = 0.85f),
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 13.sp,
                modifier = Modifier.padding(top = 5.dp, start = 2.dp, end = 2.dp)
            )
            footer?.let {
                Text(it, fontSize = 9.sp, color = SeanimeOnSurfaceMuted, modifier = Modifier.padding(start = 2.dp))
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PosterBadge(text: String, background: Color, foreground: Color) {
    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(text, fontSize = 8.sp, color = foreground, fontWeight = FontWeight.SemiBold)
    }
}
