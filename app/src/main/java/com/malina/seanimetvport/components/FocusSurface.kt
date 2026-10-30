package com.malina.seanimetvport.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.malina.seanimetvport.ui.theme.SeanimeOnSurface
import com.malina.seanimetvport.ui.theme.SeanimePrimary

/**
 * Thin wrapper over the tv-material clickable [Surface] used across the Series
 * Detail panel. It centralises the (experimental, alpha07) `ClickableSurface*`
 * API in one place — the whole panel gets D-pad focus semantics, focus border,
 * container tint and optional focus scale from tv-material instead of the
 * hand-rolled `focusable` + `MutableInteractionSource` idiom used elsewhere.
 *
 * The [content] receives the live `focused` flag so callers can adapt custom
 * inner visuals (scrims, icon tint, multi-colour text) that tv-material's
 * container/border colours cannot express on their own.
 *
 * `focusedScale` defaults to 1f (no scale). Scaling via tv-material uses a
 * graphicsLayer transform, which the automatic bring-into-view of a plain
 * `verticalScroll` container can react to and jump — so only opt into a scale
 * for elements that live inside a `TvLazyRow`/`TvLazyColumn`, which handle
 * focus scrolling natively (see [com.malina.seanimetvport.components.AnimeCard]).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    containerColor: Color = Color.Transparent,
    focusedContainerColor: Color = SeanimePrimary.copy(alpha = 0.18f),
    selectedContainerColor: Color = Color.White.copy(alpha = 0.07f),
    selected: Boolean = false,
    focusedBorderColor: Color = SeanimePrimary,
    unfocusedBorderColor: Color = Color.Transparent,
    borderWidth: Dp = 2.dp,
    focusedScale: Float = 1f,
    content: @Composable BoxScope.(focused: Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        modifier = modifier,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) selectedContainerColor else containerColor,
            focusedContainerColor = focusedContainerColor,
            pressedContainerColor = focusedContainerColor,
            contentColor = SeanimeOnSurface,
            focusedContentColor = SeanimeOnSurface,
            pressedContentColor = SeanimeOnSurface
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(borderWidth, unfocusedBorderColor), shape = shape),
            focusedBorder = Border(BorderStroke(borderWidth, focusedBorderColor), shape = shape)
        )
    ) {
        content(focused)
    }
}
