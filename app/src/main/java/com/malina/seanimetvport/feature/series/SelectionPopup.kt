package com.malina.seanimetvport.feature.series

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.malina.seanimetvport.components.FocusSurface
import com.malina.seanimetvport.ui.theme.SeanimeOnSurface
import com.malina.seanimetvport.ui.theme.SeanimeOnSurfaceMuted
import com.malina.seanimetvport.ui.theme.SeanimePrimary
import com.malina.seanimetvport.ui.theme.SeanimeSurface

private const val POPUP_WIDTH_DP = 300
private const val POPUP_DROP_DP = 44
private val cornerOrigin = TransformOrigin(0f, 0f)

/**
 * A choice list floated just below its trigger chip in a [Popup]. It scales +
 * expands in from the top-left corner and, thanks to a retained transition
 * state, plays that animation in reverse on dismiss instead of vanishing. While
 * open it is a focus island: the first row auto-focuses, focus cannot escape
 * sideways, and BACK closes it.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)
@Composable
internal fun SelectionPopup(
    expanded: Boolean,
    title: String,
    options: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onClose: () -> Unit
) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = expanded

    // Keep the Popup mounted through the whole open/close transition.
    val mounted = visibility.currentState || visibility.targetState || !visibility.isIdle
    if (!mounted) return

    val dropOffset = with(LocalDensity.current) { IntOffset(0, POPUP_DROP_DP.dp.roundToPx()) }
    Popup(
        alignment = Alignment.TopStart,
        offset = dropOffset,
        onDismissRequest = onClose,
        properties = PopupProperties(focusable = expanded)
    ) {
        val topRow = remember { FocusRequester() }
        LaunchedEffect(expanded) { if (expanded) runCatching { topRow.requestFocus() } }

        AnimatedVisibility(
            visibleState = visibility,
            enter = fadeIn(tween(150)) +
                scaleIn(tween(180), initialScale = 0.9f, transformOrigin = cornerOrigin) +
                expandVertically(tween(180), expandFrom = Alignment.Top),
            exit = fadeOut(tween(120)) +
                scaleOut(tween(150), targetScale = 0.9f, transformOrigin = cornerOrigin) +
                shrinkVertically(tween(150), shrinkTowards = Alignment.Top)
        ) {
            Column(
                modifier = Modifier
                    .width(POPUP_WIDTH_DP.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SeanimeSurface)
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                    .focusProperties { exit = { FocusRequester.Cancel } }
                    .focusGroup()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = SeanimeOnSurfaceMuted,
                    modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp)
                )
                if (options.isEmpty()) {
                    Text(
                        text = "No options available.",
                        fontSize = 12.sp,
                        color = SeanimeOnSurfaceMuted,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                options.forEachIndexed { index, (id, label) ->
                    SelectionRow(
                        label = label,
                        selected = id == selectedId,
                        onClick = { onSelect(id) },
                        modifier = if (index == 0) Modifier.focusRequester(topRow) else Modifier
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SelectionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        selected = selected,
        focusedContainerColor = SeanimePrimary.copy(alpha = 0.22f),
        selectedContainerColor = Color.White.copy(alpha = 0.07f),
        focusedBorderColor = SeanimePrimary,
        borderWidth = 1.5.dp
    ) { focused ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (focused || selected) SeanimeOnSurface else SeanimeOnSurface.copy(alpha = 0.8f)
            )
            if (selected) Text("✓", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SeanimePrimary)
        }
    }
}
