package com.malina.seanimetvport.feature.series

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.malina.seanimetvport.components.SeriesPill
import com.malina.seanimetvport.ui.theme.SeanimeOnSurfaceMuted
import com.malina.seanimetvport.ui.theme.SeanimeSurface

/**
 * Small non-focusable metadata pills used in the hero. Pure display — no D-pad
 * interaction, so a plain [Box] + tv-material [Text] is correct here.
 */

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ScoreBadge(score: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFF2D6A4F), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = "★ ${formatScore(score)}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF52B788)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun InfoChip(text: String, modifier: Modifier = Modifier, accent: Color = SeanimeOnSurfaceMuted) {
    Box(
        modifier = modifier
            .background(SeanimeSurface, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 11.sp, color = accent)
    }
}

/** Dropdown-trigger pill for the provider / server / sub-dub selectors. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SelectorChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) =
    SeriesPill(label = label, onClick = onClick, modifier = modifier)
