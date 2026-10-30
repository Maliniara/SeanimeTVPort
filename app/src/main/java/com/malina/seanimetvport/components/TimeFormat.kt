package com.malina.seanimetvport.components

/**
 * Formats a millisecond position as `m:ss`, or `h:mm:ss` once past an hour.
 * Negative or zero values render as `0:00`.
 *
 * Shared by the player's scrubber and the Home "Recently watched" cards, which
 * previously each carried their own byte-identical copy.
 */
fun formatPlaybackTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
