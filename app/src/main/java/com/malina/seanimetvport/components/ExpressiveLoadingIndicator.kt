package com.malina.seanimetvport.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import com.malina.seanimetvport.ui.theme.SeanimePrimary

/**
 * Material 3 Expressive-style loading indicator: a single filled shape that
 * continuously MORPHS through a family of rounded polygons while it spins —
 * built on `androidx.graphics.shapes`, the same primitives the real Expressive
 * `LoadingIndicator` uses under the hood (so it's authentic and independent of
 * the material3 version's API surface).
 */
private fun expressiveShapes(): List<RoundedPolygon> = listOf(
    RoundedPolygon.circle(numVertices = 12),
    RoundedPolygon.star(numVerticesPerRadius = 4, innerRadius = 0.74f, rounding = CornerRounding(0.32f)),
    RoundedPolygon.star(numVerticesPerRadius = 6, innerRadius = 0.72f, rounding = CornerRounding(0.24f)),
    RoundedPolygon(numVertices = 5, rounding = CornerRounding(0.30f)),
    RoundedPolygon.star(numVerticesPerRadius = 8, innerRadius = 0.82f, rounding = CornerRounding(0.20f))
)

@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = SeanimePrimary,
    size: Dp = 48.dp
) {
    val shapes = remember { expressiveShapes() }
    val morphs = remember(shapes) {
        shapes.indices.map { i -> Morph(shapes[i], shapes[(i + 1) % shapes.size]) }
    }

    val transition = rememberInfiniteTransition(label = "expressiveLoader")
    // t sweeps 0..morphs.size; its integer part selects the current morph and
    // the fractional part is that morph's progress.
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = morphs.size.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = morphs.size * 560, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "morphProgress"
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(modifier = modifier.size(size)) {
        val index = t.toInt().coerceIn(0, morphs.size - 1)
        val progress = (t - t.toInt()).coerceIn(0f, 1f)

        // Shapes live in a unit space centred on the origin (radius ~1). Scale
        // to the canvas, spin, and centre.
        val radiusPx = this.size.minDimension / 2f
        val androidPath = morphs[index].toPath(progress)
        val matrix = android.graphics.Matrix().apply {
            postScale(radiusPx, radiusPx)
            postRotate(rotation)
            postTranslate(this@Canvas.size.width / 2f, this@Canvas.size.height / 2f)
        }
        androidPath.transform(matrix)
        drawPath(androidPath.asComposePath(), color)
    }
}
