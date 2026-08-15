package com.example.seanimetv.feature.home

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.seanimetv.data.anilist.model.MediaListStatus
import com.example.seanimetv.ui.theme.ExpressiveShapes
import com.example.seanimetv.ui.theme.SeanimeOnSurface
import com.example.seanimetv.ui.theme.SeanimeOnSurfaceMuted
import com.example.seanimetv.ui.theme.SeanimePrimary
import com.example.seanimetv.ui.theme.SeanimeSurface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** The AniList statuses offered by the card menu, in display order. */
private val MENU_STATUSES = listOf(
    MediaListStatus.CURRENT to "Watching",
    MediaListStatus.PLANNING to "Planning",
    MediaListStatus.COMPLETED to "Completed",
    MediaListStatus.PAUSED to "Paused",
    MediaListStatus.DROPPED to "Dropped"
)

/**
 * Long-press context menu for a Home card (Recently watched / Continue watching).
 * A dimmed full-screen scrim with a centered card offering two actions: remove the
 * item from its panel, and change the series' AniList status (via a sub-list). Back
 * closes the status sub-list first, then the whole menu.
 */
@Composable
internal fun HomeCardMenu(
    title: String,
    removeLabel: String,
    currentStatus: MediaListStatus?,
    onRemove: () -> Unit,
    onChangeStatus: (MediaListStatus) -> Unit,
    onDismiss: () -> Unit
) {
    var showStatus by remember { mutableStateOf(false) }
    BackHandler { if (showStatus) showStatus = false else onDismiss() }

    val firstRow = remember { FocusRequester() }
    // Re-grab focus for the first row whenever the sub-list toggles, so the D-pad
    // never lands on a detached node.
    LaunchedEffect(showStatus) { runCatching { firstRow.requestFocus() } }

    // The long-press that opened this menu keeps streaming DPAD_CENTER down events
    // and fires one up on release. Once focus moves onto the first menu row (mid-
    // hold), those trailing events would land on it as an instant click. So we stay
    // "unarmed" until we observe that first key-up, swallowing every OK event until
    // then — the user must press OK again to actually pick an option.
    var armed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val isSelectKey = event.key == Key.DirectionCenter || event.key == Key.Enter
                if (isSelectKey && !armed) {
                    if (event.type == KeyEventType.KeyUp) armed = true
                    true // swallow the opening long-press's own down/up events
                } else {
                    false
                }
            }
            .background(Color.Black.copy(alpha = 0.78f))
            // The scrim must never take D-pad focus: there is no pointer on a TV, so
            // focusing it would show no highlight anywhere and the next OK press would
            // dismiss the menu instead of choosing an option. BACK still closes it.
            .focusProperties { canFocus = false },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .clip(ExpressiveShapes.large)
                .background(SeanimeSurface)
                .border(1.dp, Color.White.copy(alpha = 0.10f), ExpressiveShapes.large)
                .padding(20.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = SeanimeOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (showStatus) "Set status" else "Options",
                fontSize = 12.sp,
                color = SeanimeOnSurfaceMuted,
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
            )

            if (!showStatus) {
                MenuRow(
                    icon = Icons.Rounded.Delete,
                    label = removeLabel,
                    destructive = true,
                    modifier = Modifier.focusRequester(firstRow)
                ) { onRemove(); onDismiss() }
                Spacer(Modifier.height(8.dp))
                MenuRow(
                    icon = Icons.Rounded.SwapVert,
                    label = "Change status",
                    trailing = Icons.Rounded.ChevronRight
                ) { showStatus = true }
            } else {
                MENU_STATUSES.forEachIndexed { index, (status, label) ->
                    if (index > 0) Spacer(Modifier.height(8.dp))
                    MenuRow(
                        icon = if (status == currentStatus) Icons.Rounded.Check else null,
                        label = label,
                        selected = status == currentStatus,
                        modifier = if (index == 0) Modifier.focusRequester(firstRow) else Modifier
                    ) { onChangeStatus(status); onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector?,
    label: String,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    selected: Boolean = false,
    trailing: ImageVector? = null,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val contentColor = when {
        focused -> Color.White
        destructive -> Color(0xFFFC8181)
        selected -> SeanimePrimary
        else -> SeanimeOnSurface
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) SeanimePrimary else Color.White.copy(alpha = 0.05f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Fixed-width icon slot keeps labels aligned whether or not a row has one.
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
            }
        }
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Icon(trailing, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        }
    }
}
