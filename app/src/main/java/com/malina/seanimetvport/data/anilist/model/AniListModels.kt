package com.malina.seanimetvport.data.anilist.model

import kotlinx.serialization.Serializable

@Serializable
data class AniListUser(
    val id: Int,
    val name: String,
    val avatar: AniListAvatar? = null
)

@Serializable
data class AniListAvatar(
    val medium: String? = null
)

@Serializable
data class AniListStartDate(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null
)

@Serializable
data class AniListStudio(val name: String)

@Serializable
data class AniListStudioEdge(
    val isMain: Boolean = false,
    val node: AniListStudio? = null
)

@Serializable
data class AniListStudios(
    val edges: List<AniListStudioEdge> = emptyList()
)

@Serializable
data class AniListCharacterName(val full: String? = null)

@Serializable
data class AniListCharacterImage(val medium: String? = null)

@Serializable
data class AniListCharacter(
    val id: Int,
    val name: AniListCharacterName? = null,
    val image: AniListCharacterImage? = null
)

@Serializable
data class AniListCharacterEdge(
    val role: String? = null,
    val node: AniListCharacter? = null
)

@Serializable
data class AniListCharacters(
    val edges: List<AniListCharacterEdge> = emptyList()
)

@Serializable
data class AniListRelationEdge(
    val relationType: String? = null,
    val node: AniListMedia? = null
)

@Serializable
data class AniListRelations(
    val edges: List<AniListRelationEdge> = emptyList()
)

@Serializable
data class AniListRecommendation(
    val mediaRecommendation: AniListMedia? = null,
    val rating: Int? = null
)

@Serializable
data class AniListRecommendations(
    val nodes: List<AniListRecommendation> = emptyList()
)

@Serializable
data class AiringEpisode(
    val airingAt: Long? = null,
    val episode: Int = 0
)

@Serializable
data class AniListRanking(
    val rank: Int,
    val type: String? = null,
    val allTime: Boolean = false,
    val season: String? = null,
    val year: Int? = null
)

@Serializable
data class AniListFuzzyDate(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null
)

@Serializable
data class AniListMediaListEntrySimple(
    val id: Int? = null,
    val progress: Int = 0,
    val status: String? = null,
    val score: Double? = null,
    val repeat: Int = 0,
    val startedAt: AniListFuzzyDate? = null,
    val completedAt: AniListFuzzyDate? = null
)

@Serializable
data class AniListMedia(
    val id: Int,
    val idMal: Int? = null,
    val title: AniListTitle? = null,
    val coverImage: AniListCoverImage? = null,
    val episodes: Int? = null,
    val duration: Int? = null,
    val status: String? = null,
    val format: String? = null,
    val isAdult: Boolean = false,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val averageScore: Int? = null,
    val meanScore: Int? = null,
    val bannerImage: String? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val startDate: AniListStartDate? = null,
    val studios: AniListStudios? = null,
    val characters: AniListCharacters? = null,
    val relations: AniListRelations? = null,
    val recommendations: AniListRecommendations? = null,
    val rankings: List<AniListRanking> = emptyList(),
    val mediaListEntry: AniListMediaListEntrySimple? = null,
    val nextAiringEpisode: AiringEpisode? = null,
    val streamingEpisodes: List<AniListStreamingEpisode> = emptyList(),
    val trailer: AniListTrailer? = null
)

@Serializable
data class AniListStreamingEpisode(
    val title: String? = null,
    val thumbnail: String? = null,
    val url: String? = null,
    val site: String? = null
)

@Serializable
data class AniListTitle(
    val romaji: String? = null,
    val english: String? = null
)

@Serializable
data class AniListCoverImage(
    val large: String? = null,
    val extraLarge: String? = null
)

@Serializable
data class AniListMediaListEntry(
    val id: Int,
    val progress: Int,
    val status: String,
    val media: AniListMedia,
    /** The viewer's own score for this entry, in their configured score format. */
    val score: Double? = null
)

enum class MediaListStatus {
    CURRENT,
    PLANNING,
    COMPLETED,
    DROPPED,
    PAUSED,
    REPEATING
}

@Serializable
data class AiringScheduleEntry(
    val id: Int,
    val airingAt: Long,
    val timeUntilAiring: Long,
    val episode: Int,
    val media: AniListMedia
)

@Serializable
data class AniListTrailer(
    val site: String? = null,
    val id: String? = null
)
