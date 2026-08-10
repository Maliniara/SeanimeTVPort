@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.seanimetv.feature.extensions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.seanimetv.ui.theme.SeanimeOnSurface
import com.example.seanimetv.ui.theme.SeanimeOnSurfaceMuted
import com.example.seanimetv.ui.theme.SeanimePrimary
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import com.example.seanimetv.components.ActionButton
import com.example.seanimetv.components.PageTitle
import com.example.seanimetv.components.entranceReveal
import com.example.seanimetv.components.rememberEntranceProgress
import com.example.seanimetv.data.extension.MarketplaceEntry
import com.example.seanimetv.data.plugin.model.PluginEntry

internal val ChipGreen = Color(0xFF52B788)
internal val ChipTeal = Color(0xFF4CC9F0)
internal val ChipRed = Color(0xFFE5484D)
internal val ChipBlue = Color(0xFF6AA6FF)
internal val CardBg = Color(0xFF101322)

// ---------------------------------------------------------------------------
// Shared card building blocks
// ---------------------------------------------------------------------------

@Composable
internal fun ExtensionCardFrame(content: @Composable () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(
                width = 1.5.dp,
                color = if (isFocused) SeanimePrimary else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp)
            )
            .focusable(interactionSource = interactionSource)
            .padding(14.dp)
    ) {
        content()
    }
}

@Composable
internal fun ExtensionCardHeader(iconUrl: String?, name: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            if (!iconUrl.isNullOrBlank()) {
                AsyncImage(
                    model = iconUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = name.take(1).uppercase(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = SeanimeOnSurface
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = SeanimeOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = SeanimeOnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Wrapping chips row. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun ChipsRow(content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}

@Composable
internal fun Chip(text: String, color: Color, filled: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (filled) color.copy(alpha = 0.22f) else Color.Transparent)
            .border(1.dp, color.copy(alpha = if (filled) 0.6f else 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1
        )
    }
}

@Composable
internal fun SmallCardButton(
    label: String,
    onClick: () -> Unit,
    accent: Boolean = false,
    danger: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val baseColor = when {
        danger -> ChipRed
        accent -> SeanimePrimary
        else -> Color.White.copy(alpha = 0.7f)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    isFocused && danger -> ChipRed.copy(alpha = 0.25f)
                    isFocused -> SeanimePrimary.copy(alpha = 0.25f)
                    accent -> SeanimePrimary.copy(alpha = 0.15f)
                    else -> Color.White.copy(alpha = 0.06f)
                }
            )
            .border(
                width = 1.dp,
                color = if (isFocused) baseColor else baseColor.copy(alpha = 0.35f),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isFocused) SeanimeOnSurface else baseColor
        )
    }
}

@Composable
internal fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                when {
                    selected -> Color.White.copy(alpha = 0.14f)
                    isFocused -> Color.White.copy(alpha = 0.08f)
                    else -> Color.Transparent
                }
            )
            .border(
                width = 1.5.dp,
                color = if (isFocused) SeanimePrimary else Color.Transparent,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) SeanimeOnSurface else SeanimeOnSurfaceMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    selected -> SeanimePrimary.copy(alpha = 0.25f)
                    isFocused -> Color.White.copy(alpha = 0.10f)
                    else -> Color.White.copy(alpha = 0.06f)
                }
            )
            .border(
                width = 1.5.dp,
                color = if (isFocused) SeanimePrimary else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = when {
                selected -> SeanimePrimary
                isFocused -> SeanimeOnSurface
                else -> SeanimeOnSurfaceMuted
            },
            fontWeight = if (selected || isFocused) FontWeight.Medium else FontWeight.Normal
        )
    }
}
