package com.example.seanimetv.feature.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.seanimetv.components.ActionButton
import com.example.seanimetv.data.anilist.model.AniListFuzzyDate
import com.example.seanimetv.data.anilist.model.AniListMedia
import com.example.seanimetv.data.anilist.model.MediaListStatus
import com.example.seanimetv.ui.theme.SeanimeOnSurface
import com.example.seanimetv.ui.theme.SeanimeOnSurfaceMuted
import com.example.seanimetv.ui.theme.SeanimePrimary
import java.time.LocalDate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// The status wheel the Status field cycles through, in display order.
private val STATUS_WHEEL = listOf(
    MediaListStatus.CURRENT to "Watching",
    MediaListStatus.PLANNING to "Planning",
    MediaListStatus.COMPLETED to "Completed",
    MediaListStatus.DROPPED to "Dropped",
    MediaListStatus.PAUSED to "Paused",
    MediaListStatus.REPEATING to "Rewatching"
)

private fun MediaListStatus.wheelLabel() = STATUS_WHEEL.first { it.first == this }.second
private fun MediaListStatus.stepped(delta: Int): MediaListStatus {
    val i = STATUS_WHEEL.indexOfFirst { it.first == this }
    return STATUS_WHEEL[((i + delta) % STATUS_WHEEL.size + STATUS_WHEEL.size) % STATUS_WHEEL.size].first
}

private fun AniListFuzzyDate.asLocalDate(): LocalDate? {
    val (y, m, d) = Triple(year, month, day)
    return if (y != null && m != null && d != null) runCatching { LocalDate.of(y, m, d) }.getOrNull() else null
}

private fun LocalDate.asFuzzy() = AniListFuzzyDate(year = year, month = monthValue, day = dayOfMonth)

/** Descriptor for one editable field; keeps the layout loop declarative. */
private class Field(
    val key: String,
    val label: String,
    val value: String,
    val onStep: (Int) -> Unit,
    val onStepAlt: ((Int) -> Unit)? = null,
    val onBeginEdit: () -> Unit = {}
)

/**
 * Modal editor for the viewer's AniList list entry. Six fields laid out in two
 * rows; a field is armed with OK, then D-pad ◄► steps its value (▲▼ steps the
 * month on date fields) until OK/BACK disarms it. The dialog traps focus so the
 * D-pad can never wander into the screen behind it; BACK closes the whole modal.
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
internal fun EditEntryOverlay(
    media: AniListMedia,
    isSaving: Boolean,
    onSave: (MediaListStatus, Double, Int, Int, AniListFuzzyDate?, AniListFuzzyDate?) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    val entry = media.mediaListEntry

    var status by remember {
        mutableStateOf(
            entry?.status?.let { runCatching { MediaListStatus.valueOf(it) }.getOrNull() }
                ?: MediaListStatus.CURRENT
        )
    }
    var score by remember { mutableStateOf(entry?.score ?: 0.0) }
    var progress by remember { mutableStateOf(entry?.progress ?: 0) }
    var repeats by remember { mutableStateOf(entry?.repeat ?: 0) }
    var startDate by remember { mutableStateOf(entry?.startedAt?.asLocalDate()) }
    var finishDate by remember { mutableStateOf(entry?.completedAt?.asLocalDate()) }
    var armed by remember { mutableStateOf<String?>(null) }

    val episodeCap = media.episodes ?: 9999
    val firstField = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstField.requestFocus() } }

    val fields = listOf(
        Field("status", "Status", status.wheelLabel(), onStep = { status = status.stepped(it) }),
        Field("score", "Score", if (score > 0.0) "%.1f".format(score) else "–",
            onStep = { score = (score + it * 0.5).coerceIn(0.0, 10.0) }),
        Field("progress", "Progress",
            media.episodes?.let { "$progress / $it" } ?: "$progress",
            onStep = { progress = (progress + it).coerceIn(0, episodeCap) }),
        Field("start", "Start date", startDate?.toString() ?: "Select a date",
            onStep = { startDate = (startDate ?: LocalDate.now()).plusDays(it.toLong()) },
            onStepAlt = { startDate = (startDate ?: LocalDate.now()).plusMonths(it.toLong()) },
            onBeginEdit = { if (startDate == null) startDate = LocalDate.now() }),
        Field("finish", "Completion date", finishDate?.toString() ?: "Select a date",
            onStep = { finishDate = (finishDate ?: LocalDate.now()).plusDays(it.toLong()) },
            onStepAlt = { finishDate = (finishDate ?: LocalDate.now()).plusMonths(it.toLong()) },
            onBeginEdit = { if (finishDate == null) finishDate = LocalDate.now() }),
        Field("repeats", "Total rewatches", "$repeats",
            onStep = { repeats = (repeats + it).coerceIn(0, 999) })
    )

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.80f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF101322))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup()
                .padding(24.dp)
        ) {
            Text(
                text = media.title?.english ?: media.title?.romaji ?: "",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = SeanimeOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))

            fields.chunked(3).forEachIndexed { rowIndex, rowFields ->
                if (rowIndex > 0) Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    rowFields.forEach { field ->
                        EntryField(
                            field = field,
                            armed = armed == field.key,
                            onToggleArm = {
                                if (armed == field.key) {
                                    armed = null
                                } else {
                                    field.onBeginEdit()
                                    armed = field.key
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .then(if (field === fields.first()) Modifier.focusRequester(firstField) else Modifier)
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (entry?.id != null) DeleteButton(onClick = onDelete) else Spacer(Modifier.size(1.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = SeanimePrimary)
                    }
                    ActionButton(
                        label = "Save",
                        primary = true,
                        onClick = {
                            onSave(status, score, progress, repeats, startDate?.asFuzzy(), finishDate?.asFuzzy())
                        }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "OK to edit a field  ·  ◄ ► change value  ·  ▲ ▼ month (dates)",
                fontSize = 11.sp,
                color = SeanimeOnSurfaceMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun EntryField(
    field: Field,
    armed: Boolean,
    onToggleArm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val placeholder = field.value == "Select a date" || field.value == "–"

    Column(modifier = modifier) {
        Text(field.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SeanimeOnSurface)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = if (armed) 0.10f else 0.05f))
                .border(
                    width = 1.5.dp,
                    color = when {
                        armed -> SeanimePrimary
                        focused -> Color.White.copy(alpha = 0.55f)
                        else -> Color.White.copy(alpha = 0.14f)
                    },
                    shape = RoundedCornerShape(8.dp)
                )
                // While armed, swallow every D-pad key (down AND up) so focus can
                // never slip out of the field mid-edit; each press steps the value.
                .onKeyEvent { event ->
                    if (!armed || event.key !in ARMED_KEYS) return@onKeyEvent false
                    if (event.type == KeyEventType.KeyDown) when (event.key) {
                        Key.DirectionLeft -> field.onStep(-1)
                        Key.DirectionRight -> field.onStep(1)
                        Key.DirectionUp -> field.onStepAlt?.invoke(1)
                        Key.DirectionDown -> field.onStepAlt?.invoke(-1)
                        Key.Back -> onToggleArm()
                    }
                    true
                }
                .clickable(interactionSource = interaction, indication = null, onClick = onToggleArm)
                .focusable(interactionSource = interaction)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = field.value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (placeholder) SeanimeOnSurfaceMuted else SeanimeOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (armed) Text("◄ ►", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SeanimePrimary)
        }
    }
}

private val ARMED_KEYS = setOf(
    Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown, Key.Back
)

@Composable
private fun DeleteButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (focused) Color(0xFF3A1520) else Color.White.copy(alpha = 0.06f))
            .border(1.5.dp, if (focused) Color(0xFFE5484D) else Color.White.copy(alpha = 0.14f), CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Delete, contentDescription = "Delete entry", tint = Color(0xFFE5484D), modifier = Modifier.size(19.dp))
    }
}
