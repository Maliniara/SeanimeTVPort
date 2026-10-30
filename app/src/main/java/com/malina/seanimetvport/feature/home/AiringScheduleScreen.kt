package com.malina.seanimetvport.feature.home

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.malina.seanimetvport.components.PageTitle
import com.malina.seanimetvport.components.entranceReveal
import com.malina.seanimetvport.components.rememberEntranceProgress
import com.malina.seanimetvport.data.anilist.model.AiringScheduleEntry
import com.malina.seanimetvport.data.anilist.repository.AniListRepository
import com.malina.seanimetvport.ui.theme.SeanimeBackground
import com.malina.seanimetvport.ui.theme.SeanimeBorder
import com.malina.seanimetvport.ui.theme.SeanimeOnSurface
import com.malina.seanimetvport.ui.theme.SeanimeOnSurfaceMuted
import com.malina.seanimetvport.ui.theme.SeanimePrimary
import com.malina.seanimetvport.ui.theme.SeanimeSurface
import java.util.Calendar

// ---------------------------------------------------------------------------
// Data helpers
// ---------------------------------------------------------------------------

private data class CalendarDay(
    val dayOfMonth: Int,
    val entries: List<AiringScheduleEntry>
)

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

private fun monthStart(year: Int, month: Int): Calendar = Calendar.getInstance().apply {
    set(Calendar.YEAR, year)
    set(Calendar.MONTH, month)
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}

private fun monthEnd(year: Int, month: Int): Calendar = monthStart(year, month).apply {
    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
}

private fun Calendar.monBasedDow(): Int = when (get(Calendar.DAY_OF_WEEK)) {
    Calendar.MONDAY    -> 0
    Calendar.TUESDAY   -> 1
    Calendar.WEDNESDAY -> 2
    Calendar.THURSDAY  -> 3
    Calendar.FRIDAY    -> 4
    Calendar.SATURDAY  -> 5
    else               -> 6
}

// ---------------------------------------------------------------------------
// Screen — uses Column (not LazyColumn) so the full month fits on screen
// and month navigation doesn't cause focus loss / sidebar re-open
// ---------------------------------------------------------------------------

@Composable
fun AiringScheduleScreen(
    aniListRepository: AniListRepository,
    userMediaIds: Set<Int> = emptySet(),
    modifier: Modifier = Modifier
) {
    val today = remember { Calendar.getInstance() }
    var displayYear by remember { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var displayMonth by remember { mutableIntStateOf(today.get(Calendar.MONTH)) }
    var allEntries by remember { mutableStateOf<List<AiringScheduleEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(displayYear, displayMonth) {
        isLoading = true
        errorMessage = null
        val start = monthStart(displayYear, displayMonth)
        val end = monthEnd(displayYear, displayMonth)
        val fromSec = (start.timeInMillis / 1000).toInt()
        val toSec = (end.timeInMillis / 1000).toInt()
        aniListRepository.getCollectionAiringSchedule(
            mediaIds = userMediaIds.toList(),
            fromUnixSeconds = fromSec,
            toUnixSeconds = toSec
        ).onSuccess { entries ->
            allEntries = entries
        }.onFailure { e ->
            errorMessage = e.message ?: "Failed to load"
        }
        isLoading = false
    }

    val filteredEntries by remember(allEntries) {
        derivedStateOf { allEntries }
    }

    val calendarDays by remember(filteredEntries, displayYear, displayMonth) {
        derivedStateOf {
            val grouped = filteredEntries.groupBy { entry ->
                Calendar.getInstance().apply { timeInMillis = entry.airingAt * 1000L }
                    .get(Calendar.DAY_OF_MONTH)
            }
            val daysInMonth = monthStart(displayYear, displayMonth).getActualMaximum(Calendar.DAY_OF_MONTH)
            (1..daysInMonth).map { day ->
                CalendarDay(dayOfMonth = day, entries = grouped[day] ?: emptyList())
            }
        }
    }

    val firstDayOffset by remember(displayYear, displayMonth) {
        derivedStateOf { monthStart(displayYear, displayMonth).monBasedDow() }
    }

    val rowCount by remember(firstDayOffset, calendarDays) {
        derivedStateOf { (firstDayOffset + calendarDays.size + 6) / 7 }
    }

    // Outer Box: calendar stays mounted during loading → no focus loss
    Box(modifier = modifier.fillMaxSize().background(SeanimeBackground)) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 14.dp)
        ) {
            // ── Title ─────────────────────────────────────────────────────
            PageTitle(
                text = "Release schedule",
                modifier = Modifier.entranceReveal(rememberEntranceProgress())
            )
            Text(
                text = "Based on your anime list",
                fontSize = 12.sp,
                color = SeanimeOnSurfaceMuted,
                modifier = Modifier.padding(start = 13.dp)
            )
            Spacer(Modifier.height(10.dp))

            // ── Month navigation ───────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MonthNavButton("<") {
                    val prev = monthStart(displayYear, displayMonth).apply { add(Calendar.MONTH, -1) }
                    displayYear = prev.get(Calendar.YEAR)
                    displayMonth = prev.get(Calendar.MONTH)
                }
                Text(
                    text = "${MONTH_NAMES[displayMonth]} $displayYear",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SeanimeOnSurface
                )
                MonthNavButton(">") {
                    val next = monthStart(displayYear, displayMonth).apply { add(Calendar.MONTH, 1) }
                    displayYear = next.get(Calendar.YEAR)
                    displayMonth = next.get(Calendar.MONTH)
                }
            }
            Spacer(Modifier.height(8.dp))

            // ── Day-of-week header ─────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth()) {
                DAY_LABELS.forEach { label ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SeanimeOnSurfaceMuted,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(5.dp))

            // ── Calendar grid — fills all remaining height ─────────────────
            // Using weight(1f) rows inside a Column so the full month is
            // always visible without scrolling regardless of row count.
            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                repeat(rowCount) { rowIndex ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(bottom = if (rowIndex < rowCount - 1) 3.dp else 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        repeat(7) { col ->
                            val cellIndex = rowIndex * 7 + col
                            val dayIndex = cellIndex - firstDayOffset
                            val day = calendarDays.getOrNull(dayIndex)

                            if (day == null) {
                                Box(modifier = Modifier.weight(1f).fillMaxHeight())
                            } else {
                                val isToday = day.dayOfMonth == today.get(Calendar.DAY_OF_MONTH) &&
                                    displayMonth == today.get(Calendar.MONTH) &&
                                    displayYear == today.get(Calendar.YEAR)
                                CalendarDayCell(
                                    day = day,
                                    isToday = isToday,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            }
                        }
                    }
                }
            }
        }

        // Loading overlay — calendar stays visible underneath
        if (isLoading) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SeanimePrimary)
            }
        }

        // Error notice at bottom
        errorMessage?.let { msg ->
            if (!isLoading) {
                Text(
                    text = msg,
                    color = Color(0xFFFF6B6B),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Calendar day cell
// ---------------------------------------------------------------------------

@Composable
private fun CalendarDayCell(
    day: CalendarDay,
    isToday: Boolean,
    modifier: Modifier = Modifier
) {
    val firstCoverUrl = day.entries.firstOrNull()?.media?.coverImage?.large

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(SeanimeSurface.copy(alpha = 0.28f))
            .border(
                width = if (isToday) 1.dp else 0.5.dp,
                color = if (isToday) SeanimePrimary.copy(alpha = 0.7f) else SeanimeBorder.copy(alpha = 0.22f),
                shape = RoundedCornerShape(5.dp)
            )
    ) {
        // Cover image background
        if (firstCoverUrl != null) {
            AsyncImage(
                model = firstCoverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = if (isToday) 0.5f else 0.22f }
            )
        }

        // Dark gradient from top (transparent) to bottom (near-black)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.35f to Color.Black.copy(alpha = 0.45f),
                            1.0f to Color.Black.copy(alpha = 0.90f)
                        )
                    )
                )
        )

        // Day number — top-left
        Box(
            modifier = Modifier
                .padding(4.dp)
                .size(18.dp)
                .background(
                    color = if (isToday) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(9.dp)
                )
                .align(Alignment.TopStart),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${day.dayOfMonth}",
                fontSize = 9.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                color = if (isToday) Color.Black else SeanimeOnSurfaceMuted
            )
        }

        // Entry list anchored to bottom
        if (day.entries.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                val displayed = day.entries.take(4)
                val overflow = day.entries.size - 4

                displayed.forEach { entry -> AiringEntryRow(entry) }

                if (overflow > 0) {
                    Text(
                        text = "+$overflow more",
                        fontSize = 7.sp,
                        color = SeanimeOnSurfaceMuted
                    )
                }
            }
        }
    }
}

// Text-only row: truncated title left, Ep.N right (matches Seanime original)
@Composable
private fun AiringEntryRow(entry: AiringScheduleEntry) {
    val title = entry.media.title?.english ?: entry.media.title?.romaji ?: "Unknown"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 8.sp,
            color = SeanimeOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "Ep.${entry.episode}",
            fontSize = 7.sp,
            color = SeanimeOnSurfaceMuted,
            modifier = Modifier.padding(start = 2.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Controls
// ---------------------------------------------------------------------------

@Composable
private fun MonthNavButton(label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .size(30.dp)
            .background(
                if (isFocused) SeanimePrimary.copy(alpha = 0.15f) else SeanimeSurface,
                RoundedCornerShape(4.dp)
            )
            .border(1.dp, if (isFocused) SeanimePrimary else SeanimeBorder, RoundedCornerShape(4.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (isFocused) SeanimePrimary else SeanimeOnSurface
        )
    }
}

