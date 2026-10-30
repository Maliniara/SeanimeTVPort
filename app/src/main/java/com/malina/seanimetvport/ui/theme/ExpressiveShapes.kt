package com.malina.seanimetvport.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive shape tokens. Expressive leans ROUNDER than baseline
 * M3 — larger corner radii on cards, surfaces, chips and buttons. Centralised
 * here so every surface shares one rounding language.
 */
object ExpressiveShapes {
    /** Chips, small pills, tags. */
    val small = RoundedCornerShape(12.dp)
    /** Cards, tiles, list items — the workhorse. */
    val medium = RoundedCornerShape(16.dp)
    /** Panels, sheets, dialogs, menus. */
    val large = RoundedCornerShape(24.dp)
    /** Hero/feature surfaces. */
    val extraLarge = RoundedCornerShape(32.dp)
    /** Fully rounded (buttons, avatars). */
    val full = RoundedCornerShape(50)
}
