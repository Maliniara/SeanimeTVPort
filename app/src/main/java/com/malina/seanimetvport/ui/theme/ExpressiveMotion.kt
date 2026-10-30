package com.malina.seanimetvport.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Material 3 Expressive motion tokens. Expressive replaces flat tweens with
 * SPRINGS: spatial springs (a little bounce) for things that move/resize, and
 * effects springs (no bounce) for things that fade/recolour. Reusable across
 * the whole UI so motion reads as one system.
 *
 * Values mirror the Expressive MotionScheme's spatial/effects families.
 */
object ExpressiveMotion {

    /** Spatial — fast, slight overshoot. For focus scale, small position moves. */
    fun <T> spatialFast(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.75f,
        stiffness = Spring.StiffnessMediumLow
    )

    /** Spatial — default. For card scale, panel/hero transitions. */
    fun <T> spatialDefault(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessLow
    )

    /** Spatial — expressive bounce. For entrances / attention moments. */
    fun <T> spatialExpressive(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    /** Effects — no bounce. For alpha, colour, dim, non-spatial fades. */
    fun <T> effects(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 1f,
        stiffness = Spring.StiffnessMedium
    )
}
