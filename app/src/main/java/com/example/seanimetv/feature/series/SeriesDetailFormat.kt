package com.example.seanimetv.feature.series

import com.example.seanimetv.data.anilist.model.AniListMedia
import java.text.DateFormatSymbols

/**
 * Pure string/format helpers for the Series Detail panel. Kept separate from the
 * composables so the presentation logic is unit-friendly and the UI files stay
 * about layout.
 */

private val htmlTag = Regex("<[^>]+>")
private val htmlBreak = Regex("(?i)<br\\s*/?>")
private val htmlEntities = mapOf(
    "&amp;" to "&",
    "&lt;" to "<",
    "&gt;" to ">",
    "&quot;" to "\"",
    "&#039;" to "'",
    "&nbsp;" to " "
)

/** Turns AniList's lightly-marked-up synopsis into plain multi-line text. */
internal fun String.stripHtml(): String {
    var text = htmlBreak.replace(this, "\n")
    text = htmlTag.replace(text, "")
    for ((entity, char) in htmlEntities) text = text.replace(entity, char)
    return text.trim()
}

/**
 * Human label for the airing window, e.g. "Apr 2026 · Spring", "Spring 2026" or
 * "2026" depending on how much date detail AniList returned. Null when there is
 * nothing to show.
 */
internal fun formatSeason(media: AniListMedia): String? {
    val year = media.startDate?.year ?: media.seasonYear ?: return null
    val season = media.season?.lowercase()?.replaceFirstChar(Char::uppercase)
    val month = media.startDate?.month?.let { m ->
        DateFormatSymbols().shortMonths.getOrNull(m - 1)?.takeIf { it.isNotBlank() }
            ?.replaceFirstChar(Char::uppercase)
    }
    return when {
        month != null -> listOfNotNull("$month $year", season).joinToString(" · ")
        season != null -> "$season $year"
        else -> "$year"
    }
}

/** Maps AniList's raw media status enum onto a friendly label. */
internal fun statusLabel(status: String): String = when (status.uppercase()) {
    "FINISHED" -> "Completed"
    "RELEASING" -> "Airing"
    "NOT_YET_RELEASED" -> "Upcoming"
    "CANCELLED" -> "Cancelled"
    "HIATUS" -> "On Hiatus"
    else -> status.lowercase().replaceFirstChar(Char::uppercase)
}

/** AniList scores are 0–100; show them as 0–10, hiding a trailing ".0". */
internal fun formatScore(score: Int): String {
    val tenths = score / 10.0
    return if (score % 10 == 0) tenths.toInt().toString() else "%.1f".format(tenths)
}
