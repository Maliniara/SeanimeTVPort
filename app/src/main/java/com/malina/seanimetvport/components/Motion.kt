package com.malina.seanimetvport.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * False while a startup overlay (splash) still covers the app. Entrance
 * choreography waits for it — otherwise the animations play hidden under
 * the overlay and the reveal shows already-settled content.
 */
val LocalContentRevealed = compositionLocalOf { true }

/**
 * True on memory-constrained Android TV boxes (e.g. Chromecast with Google TV HD).
 * Entrance animations and the offscreen edge-fade are skipped when set — the
 * per-frame CPU/GPU cost isn't worth it on a single-core Mali-G31. Provided once
 * at the app root from `ActivityManager.isLowRamDevice`.
 */
val LocalIsLowRamDevice = staticCompositionLocalOf { false }

/**
 * Progress value that animates 0 → 1 once, after the first composition frame
 * AND once [LocalContentRevealed] is true. Stagger multiple elements by
 * passing increasing [delayMillis]. On low-RAM devices it snaps straight to 1
 * (content simply appears — no animation cost).
 */
@Composable
fun rememberEntranceProgress(delayMillis: Int = 0, durationMillis: Int = 550): Float {
    val lowRam = LocalIsLowRamDevice.current
    val revealed = LocalContentRevealed.current
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(revealed) {
        if (revealed) started = true
    }
    val progress by animateFloatAsState(
        targetValue = if (started || lowRam) 1f else 0f,
        animationSpec = if (lowRam) snap() else tween(
            durationMillis = durationMillis,
            delayMillis = delayMillis,
            easing = FastOutSlowInEasing
        ),
        label = "entranceProgress"
    )
    return progress
}

/**
 * Entrance reveal used by screen choreography: element rises 26dp while
 * fading in. Pure graphicsLayer (no composition change) so focus targets
 * inside revealed content exist from the very first frame — never use
 * AnimatedVisibility for elements that must receive initial TV focus.
 */
fun Modifier.entranceReveal(progress: Float): Modifier = graphicsLayer {
    alpha = progress
    translationY = (1f - progress) * 26.dp.toPx()
}

/**
 * Alpha-masks the left/right edges of a horizontally scrolling row so cards
 * dissolve at the screen boundary instead of clipping hard against it.
 *
 * This uses an OFFSCREEN compositing layer + `BlendMode.DstIn`, which is one of
 * the most expensive things you can do per row on a weak GPU — so on low-RAM
 * TV boxes it is skipped entirely (cards just clip at the edge, which is fine).
 */
@Composable
fun Modifier.horizontalFadingEdges(fadeWidth: Dp = 48.dp): Modifier {
    // True alpha dissolve on every device tier: cards fade to TRANSPARENT at
    // the row bounds (background shows through), which needs the offscreen
    // DstIn mask. A background-tinted overlay was tried for low-RAM boxes and
    // looked wrong over the ambient backdrop.
    return this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val w = fadeWidth.toPx()
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                1f to Color.Black,
                startX = 0f,
                endX = w
            ),
            size = Size(w, size.height),
            blendMode = BlendMode.DstIn
        )
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Black,
                1f to Color.Transparent,
                startX = size.width - w,
                endX = size.width
            ),
            topLeft = Offset(size.width - w, 0f),
            size = Size(w, size.height),
            blendMode = BlendMode.DstIn
        )
    }
}
