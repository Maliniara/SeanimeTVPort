package com.example.seanimetv.components

import androidx.compose.runtime.compositionLocalOf
import com.example.seanimetv.data.settings.AnimeSettings

/**
 * Ambient access to the user's [AnimeSettings] (Denshi-style presentation prefs).
 * Provided once in MainActivity from the DataStore-backed flow; composables read
 * it to hide the audience score, blur/hide adult content, apply spoiler-safe
 * episode art, and disable trailers. Uses [compositionLocalOf] so a change
 * recomposes only the readers.
 */
val LocalAnimeSettings = compositionLocalOf { AnimeSettings() }
