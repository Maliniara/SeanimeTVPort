package com.malina.seanimetvport.feature.series

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.malina.seanimetvport.components.LocalAnimeSettings
import com.malina.seanimetvport.components.SeriesPill
import com.malina.seanimetvport.components.entranceReveal
import com.malina.seanimetvport.data.anilist.model.AniListMedia
import com.malina.seanimetvport.ui.theme.ExpressiveShapes
import com.malina.seanimetvport.ui.theme.SeanimePrimary

/**
 * Cinematic hero row: cover art + title/metadata/synopsis + action buttons.
 * Rendered inside the parallax overlay Box (which owns the scroll-driven fade),
 * so this composable is layout-only. Every element is always composed and only
 * alpha/translated via [entranceReveal] so the action buttons own D-pad focus
 * from the first frame (never `AnimatedVisibility` for focusable content).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SeriesHero(
    media: AniListMedia,
    hasPlugin: Boolean,
    hasTrailer: Boolean,
    revealCover: Float,
    revealTitle: Float,
    revealMeta: Float,
    revealDesc: Float,
    revealButtons: Float,
    watchButtonModifier: Modifier,
    trailerButtonModifier: Modifier,
    editButtonModifier: Modifier,
    onWatch: () -> Unit,
    onTrailer: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val anime = LocalAnimeSettings.current
    val title = media.title?.english ?: media.title?.romaji ?: "Unknown"
    val romaji = if (media.title?.english != null) media.title.romaji else null
    val score = media.averageScore ?: media.meanScore
    val mainStudio = media.studios?.edges?.firstOrNull { it.isMain }?.node?.name
        ?: media.studios?.edges?.firstOrNull()?.node?.name
    val seasonLabel = formatSeason(media)
    val progress = media.mediaListEntry?.progress ?: 0
    val topRankings = media.rankings
        .filter { it.type == "RATED" || it.type == "POPULAR" }
        .sortedBy { it.rank }.take(2)
    val availableEpisodeCount = when {
        media.status == "RELEASING" && media.nextAiringEpisode != null ->
            (media.nextAiringEpisode.episode - 1).coerceAtLeast(0)
        else -> media.episodes ?: 0
    }
    val coverUrl = media.coverImage?.extraLarge ?: media.coverImage?.large

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(140.dp)
                    .fillMaxHeight()
                    .entranceReveal(revealCover)
                    .clip(ExpressiveShapes.small)
                    .border(1.dp, Color.White.copy(alpha = 0.12f), ExpressiveShapes.small)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 34.sp,
                modifier = Modifier.entranceReveal(revealTitle)
            )
            if (romaji != null) {
                Text(
                    text = romaji,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.entranceReveal(revealTitle)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.entranceReveal(revealMeta)
            ) {
                if (score != null && !anime.hideAudienceScore) ScoreBadge(score)
                if (availableEpisodeCount > 0) {
                    val epLabel = if (media.status == "RELEASING" && media.nextAiringEpisode != null) {
                        if (progress > 0) "$progress / $availableEpisodeCount aired" else "$availableEpisodeCount aired"
                    } else {
                        if (progress > 0) "$progress / $availableEpisodeCount" else "$availableEpisodeCount ep."
                    }
                    InfoChip(epLabel)
                }
                if (media.status != null) InfoChip(statusLabel(media.status))
                if (seasonLabel != null) InfoChip(seasonLabel)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.entranceReveal(revealMeta)
            ) {
                if (mainStudio != null) {
                    Text(mainStudio, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SeanimePrimary)
                }
                if (media.format != null) {
                    Text(media.format.replace("_", " "), fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
                }
                if (media.genres.isNotEmpty()) {
                    Text(
                        text = media.genres.take(4).joinToString("  ·  "),
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (topRankings.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.entranceReveal(revealMeta)
                ) {
                    topRankings.forEach { r ->
                        val label = buildString {
                            append("#${r.rank} ")
                            if (r.allTime) {
                                append(if (r.type == "RATED") "Highest Rated" else "Most Popular")
                                if (media.format != null) append(" ${media.format}")
                            } else {
                                if (r.season != null) append(r.season.lowercase().replaceFirstChar { it.uppercase() } + " ")
                                if (r.year != null) append(r.year)
                            }
                        }
                        InfoChip(label, accent = Color(0xFFFFD700))
                    }
                }
            }
            if (!media.description.isNullOrBlank()) {
                Text(
                    text = media.description.stripHtml(),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.62f),
                    lineHeight = 17.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.entranceReveal(revealDesc)
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.entranceReveal(revealButtons)
            ) {
                if (hasPlugin) {
                    SeriesPill(label = "Watch", onClick = onWatch, modifier = watchButtonModifier)
                }
                SeriesPill(label = "Edit", onClick = onEdit, modifier = editButtonModifier)
                if (hasTrailer && !anime.disableCardTrailers) {
                    SeriesPill(label = "Trailer", onClick = onTrailer, modifier = trailerButtonModifier)
                }
            }
        }
    }
}
