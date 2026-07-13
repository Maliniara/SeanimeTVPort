package com.example.seanimetv.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.seanimetv.ui.theme.ExpressiveShapes
import com.example.seanimetv.ui.theme.SeanimeOnSurface
import com.example.seanimetv.ui.theme.SeanimePrimary

/**
 * The one focusable "pill" shared across the app — episode range chips, the
 * Row/List toggle, provider/server/sub selectors, and the hero action buttons on
 * both the Series Detail card and Home. Keeping it in `components` (not a feature
 * package) lets every screen speak one visual language without a cross-feature
 * import. [selected] tints it as the active choice (range chips / toggle).
 *
 * Border + tint only (no focus scale): it lives inside plain `verticalScroll`
 * containers whose bring-into-view would otherwise react to a scale transform.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SeriesPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier,
        shape = ExpressiveShapes.small,
        containerColor = if (selected) SeanimePrimary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
        focusedContainerColor = SeanimePrimary.copy(alpha = 0.30f),
        selected = selected,
        selectedContainerColor = SeanimePrimary.copy(alpha = 0.22f),
        focusedBorderColor = SeanimePrimary,
        unfocusedBorderColor = if (selected) SeanimePrimary.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.10f),
        borderWidth = 1.5.dp
    ) { focused ->
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 13.sp,
            // Fixed line height so leading glyphs (▶ / ✎) don't make some pills taller
            // than plain-text ones (e.g. Trailer) — every pill is the same height.
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected || focused) SeanimeOnSurface else SeanimeOnSurface.copy(alpha = 0.85f)
        )
    }
}
