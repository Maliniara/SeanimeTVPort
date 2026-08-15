package com.example.seanimetv.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.example.seanimetv.ui.theme.SeanimePrimary
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun Scrubber(positionMs: Long, durationMs: Long, bufferedMs: Long, isPlaying: Boolean) {
    val played = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val buffered = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    // Material 3 Expressive: the played portion is a WAVE that ripples while playing
    // and settles flat when paused (amplitude animates); its phase scrolls forever.
    val amplitude by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(450),
        label = "waveAmp"
    )
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase"
    )

    val inactiveColor = Color.White.copy(alpha = 0.20f)
    val bufferedColor = Color.White.copy(alpha = 0.32f)

    Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
        val w = size.width
        val cy = size.height / 2f
        val stroke = 5.dp.toPx()
        val thumbW = 6.dp.toPx()
        val thumbH = 22.dp.toPx()
        val gap = 8.dp.toPx()
        val amp = amplitude * 4.5.dp.toPx()
        val playedX = w * played
        val activeEnd = (playedX - gap).coerceAtLeast(0f)
        val afterThumb = (playedX + gap).coerceAtMost(w)

        // Inactive track (right of the thumb), with a gap around the thumb.
        if (afterThumb < w) {
            drawLine(inactiveColor, Offset(afterThumb, cy), Offset(w, cy), stroke, StrokeCap.Round)
        }
        // Buffered ahead of the playhead.
        val bufferedX = (w * buffered).coerceAtMost(w)
        if (bufferedX > afterThumb) {
            drawLine(bufferedColor, Offset(afterThumb, cy), Offset(bufferedX, cy), stroke, StrokeCap.Round)
        }
        // Active WAVE (left of the thumb).
        if (activeEnd > 1f) {
            val path = Path()
            val wavelength = 24.dp.toPx()
            val step = 3.dp.toPx()
            path.moveTo(0f, cy)
            var x = 0f
            while (x <= activeEnd) {
                val y = cy + amp * sin((x / wavelength) * 2f * PI.toFloat() + phase)
                path.lineTo(x, y)
                x += step
            }
            drawPath(path = path, color = SeanimePrimary, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
        // Thumb — a rounded pill.
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(playedX - thumbW / 2f, cy - thumbH / 2f),
            size = Size(thumbW, thumbH),
            cornerRadius = CornerRadius(thumbW / 2f, thumbW / 2f)
        )
    }
}

// ---------------------------------------------------------------------------
// Transport & setting buttons
// ---------------------------------------------------------------------------

@Composable
internal fun TransportButton(
    icon: ImageVector,
    contentDescription: String,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, tween(140), label = "transportScale")
    val diameter = if (primary) 64.dp else 50.dp
    val iconSize = if (primary) 34.dp else 26.dp

    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(diameter)
            .clip(CircleShape)
            .background(
                when {
                    focused -> SeanimePrimary
                    primary -> Color.White.copy(alpha = 0.16f)
                    else -> Color.White.copy(alpha = 0.10f)
                }
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction),
        contentAlignment = Alignment.Center
    ) {
        // Expressive play↔pause morph: the icon scales + fades through on change.
        AnimatedContent(
            targetState = icon,
            transitionSpec = {
                (fadeIn(tween(200)) + scaleIn(initialScale = 0.6f, animationSpec = tween(200)))
                    .togetherWith(fadeOut(tween(200)) + scaleOut(targetScale = 0.6f, animationSpec = tween(200)))
            },
            label = "transportIcon"
        ) { ic ->
            Icon(ic, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
internal fun SettingButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (focused) SeanimePrimary else Color.White.copy(alpha = 0.12f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun RetryButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Box(
        modifier = Modifier
            .focusRequester(fr)
            .clip(RoundedCornerShape(24.dp))
            .background(if (focused) SeanimePrimary else Color.White.copy(alpha = 0.14f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 24.dp, vertical = 11.dp)
    ) {
        Text("Try again", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------------------
// Settings sheet (quality / subtitles)
// ---------------------------------------------------------------------------

