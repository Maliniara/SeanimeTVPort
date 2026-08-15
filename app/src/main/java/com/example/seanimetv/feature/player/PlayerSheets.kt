package com.example.seanimetv.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.example.seanimetv.data.player.SubtitleStyle
import com.example.seanimetv.ui.theme.SeanimeOnSurface
import com.example.seanimetv.ui.theme.SeanimeOnSurfaceMuted
import com.example.seanimetv.ui.theme.SeanimePrimary
import com.example.seanimetv.ui.theme.SeanimeSurface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun SettingsSheet(
    title: String,
    subtitle: String,
    items: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.45f to Color.Black.copy(alpha = 0.35f),
                    1f to Color.Black.copy(alpha = 0.7f)
                )
            )
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Back) { onClose(); true } else false
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .fillMaxHeight()
                .background(SeanimeSurface.copy(alpha = 0.97f))
                .padding(horizontal = 28.dp, vertical = 40.dp)
        ) {
            Text(title, color = SeanimeOnSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = SeanimeOnSurfaceMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 22.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(items.size) { i ->
                    val (label, selected) = items[i]
                    SheetRow(label, selected, if (i == 0) firstFocus else null) { onSelect(i) }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(label: String, selected: Boolean, focusRequester: FocusRequester?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) SeanimePrimary else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (focused) Color.White else SeanimeOnSurface.copy(alpha = if (selected) 1f else 0.8f),
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "Selected",
                tint = if (focused) Color.White else SeanimePrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Subtitle sheet — track + style (size, colour, background, position)
// ---------------------------------------------------------------------------

private val SUBTITLE_COLORS = listOf(
    0xFFFFFFFF.toInt(), // white
    0xFFFFEB3B.toInt(), // yellow
    0xFF00E5FF.toInt(), // cyan
    0xFF69F0AE.toInt(), // green
    0xFFFFAB40.toInt()  // orange
)

@Composable
internal fun SubtitleSheet(
    tracks: List<SubtitleOption>,
    style: SubtitleStyle,
    onSelectTrack: (Int) -> Unit,
    onSizeChange: (Float) -> Unit,
    onColor: (Int) -> Unit,
    onToggleBackground: () -> Unit,
    onReposition: () -> Unit,
    onCenter: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.45f to Color.Black.copy(alpha = 0.35f),
                    1f to Color.Black.copy(alpha = 0.7f)
                )
            )
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Back) { onClose(); true } else false
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .fillMaxHeight()
                .background(SeanimeSurface.copy(alpha = 0.97f))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text("Subtitles", color = SeanimeOnSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)

            SectionLabel("Track")
            tracks.forEachIndexed { i, t ->
                SheetRow(t.label, t.isSelected, if (i == 0) firstFocus else null) { onSelectTrack(i) }
            }

            SectionLabel("Text size")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StepButton("–") { onSizeChange(-2f) }
                Text("${style.textSizeSp.toInt()} sp", color = SeanimeOnSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                StepButton("+") { onSizeChange(2f) }
            }

            SectionLabel("Color")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SUBTITLE_COLORS.forEach { c ->
                    ColorSwatch(color = c, selected = c == style.foregroundColor) { onColor(c) }
                }
            }

            SectionLabel("Background")
            SheetActionRow(
                label = "Background box",
                trailing = if (style.backgroundEnabled) "On" else "Off",
                onClick = onToggleBackground
            )

            SectionLabel("Position")
            SheetActionRow("Reposition subtitles", onClick = onReposition)
            SheetActionRow("Center horizontally", onClick = onCenter)
            SheetActionRow("Restore defaults", onClick = onReset)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = SeanimeOnSurfaceMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (focused) SeanimePrimary else Color.White.copy(alpha = 0.12f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ColorSwatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(
                width = if (focused || selected) 3.dp else 1.dp,
                color = if (focused) SeanimePrimary else if (selected) Color.White else Color.White.copy(alpha = 0.25f),
                shape = CircleShape
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
    )
}

@Composable
private fun SheetActionRow(label: String, trailing: String? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) SeanimePrimary else Color.White.copy(alpha = 0.06f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = if (focused) Color.White else SeanimeOnSurface, fontSize = 15.sp)
        if (trailing != null) {
            Text(trailing, color = if (focused) Color.White else SeanimePrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun SubtitleRepositionOverlay(style: SubtitleStyle) {
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 44.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 22.dp, vertical = 11.dp)
        ) {
            Text(
                "Move subtitles with the D-pad  ·  OK to finish",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .offset(x = style.offsetXDp.dp, y = style.offsetYDp.dp)
                .then(
                    if (style.backgroundEnabled)
                        Modifier.clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.6f))
                    else Modifier
                )
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                "Subtitle preview",
                color = Color(style.foregroundColor),
                fontSize = style.textSizeSp.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

