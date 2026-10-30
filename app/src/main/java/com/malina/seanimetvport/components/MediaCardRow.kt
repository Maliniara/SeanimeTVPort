package com.malina.seanimetvport.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The one horizontal card row used across the browse-style panels (My Library,
 * Discovery, Home). The row spans the FULL width and carries its side inset as
 * `contentPadding`, so [horizontalFadingEdges] dissolves cards over the padding
 * gap and at the true screen edge — the first card stays crisp and cards scroll
 * cleanly off-screen.
 *
 * This is deliberately shared (was previously duplicated, per-screen, with the
 * inset on an outer Column — which made the fade land on the first card and cut
 * cards before the right edge). [startInset] is the screen's left gutter (the nav
 * rail width on rail screens, or a plain margin elsewhere).
 */
@Composable
fun MediaCardRow(
    modifier: Modifier = Modifier,
    startInset: Dp = 108.dp,
    endInset: Dp = 32.dp,
    itemSpacing: Dp = 10.dp,
    content: LazyListScope.() -> Unit
) {
    LazyRow(
        modifier = modifier.fillMaxWidth().horizontalFadingEdges(),
        contentPadding = PaddingValues(start = startInset, end = endInset, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        content = content
    )
}
