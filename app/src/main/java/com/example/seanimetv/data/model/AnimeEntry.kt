package com.example.seanimetv.data.model

data class AnimeEntry(
    val id: Int,
    val title: String,
    val coverImageUrl: String,
    val bannerImageUrl: String? = null,
    val progress: Int = 0,
    val totalEpisodes: Int? = null,
    /** Episode number of the next episode to AIR (AniList), null when finished/unknown. */
    val nextAiringEpisode: Int? = null,
    /** AniList adult (18+) flag — drives hide/blur via the Anime settings. */
    val isAdult: Boolean = false
)
