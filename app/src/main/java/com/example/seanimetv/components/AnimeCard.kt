package com.example.seanimetv.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.clickable
import kotlinx.coroutines.delay
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.seanimetv.ui.theme.ExpressiveMotion
import com.example.seanimetv.ui.theme.ExpressiveShapes
import com.example.seanimetv.ui.theme.SeanimePrimary
import com.example.seanimetv.ui.theme.SeanimeOnSurface
import com.example.seanimetv.ui.theme.SeanimeTVTheme

private val CardWidth = 150.dp
// Expressive: rounder card corners.
private val CardShape = ExpressiveShapes.medium

@Composable
fun AnimeCard(
    title: String,
    coverImageUrl: String,
    onClick: () -> Unit,
    onFocus: () -> Unit = {},
    showProgress: Boolean = false,
    progressFraction: Float = 0f,
    isAdult: Boolean = false,
    showCoverSpinner: Boolean = false,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    // Blur adult-flagged covers when the user opted into "Blur adult content".
    val blurAdult = isAdult && LocalAnimeSettings.current.blurAdultContent

    LaunchedEffect(isFocused) {
        if (isFocused) {
            delay(150)
            onFocus()
        }
    }

    // Expressive motion: springy focus scale instead of a flat tween.
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = ExpressiveMotion.spatialFast(),
        label = "cardScale"
    )

    Column(
        modifier = modifier
            .width(CardWidth)
            // Draw-level scale: unlike graphicsLayer, a canvas transform does
            // NOT change the node's reported coordinates, so the focus
            // system's bring-into-view never sees a "moving" rect and the
            // outer scroll container stays perfectly still during the
            // focus animation.
            .drawWithContent {
                val s = scale
                scale(scale = s, pivot = center) {
                    this@drawWithContent.drawContent()
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(CardShape)
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
                    color = if (isFocused) SeanimePrimary else Color.Transparent,
                    shape = CardShape
                )
        ) {
            val coverModifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .then(if (blurAdult) Modifier.blur(18.dp) else Modifier)
            if (showCoverSpinner) {
                // M3 Expressive spinner while the cover streams in (Show All grid).
                SubcomposeAsyncImage(
                    model = coverImageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = coverModifier,
                    loading = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ExpressiveLoadingIndicator(size = 24.dp, color = SeanimePrimary)
                        }
                    }
                )
            } else {
                AsyncImage(
                    model = coverImageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = coverModifier
                )
            }
            if (showProgress) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .testTag("progress_indicator")
                        .semantics {
                            progressBarRangeInfo = ProgressBarRangeInfo(progressFraction, 0f..1f)
                        },
                    color = SeanimePrimary,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            }
        }
        Text(
            text = title,
            // minLines reserves both lines even for short titles — variable
            // title heights made LazyRow height fluctuate as items recycled,
            // bouncing all content below the row
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp,
            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isFocused) SeanimeOnSurface else SeanimeOnSurface.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0C0E14)
@Composable
fun AnimeCardPreview() {
    SeanimeTVTheme {
        AnimeCard(
            title = "Cowboy Bebop",
            coverImageUrl = "",
            onClick = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0C0E14)
@Composable
fun AnimeCardWithProgressPreview() {
    SeanimeTVTheme {
        AnimeCard(
            title = "Attack on Titan",
            coverImageUrl = "",
            onClick = {},
            showProgress = true,
            progressFraction = 0.68f
        )
    }
}
