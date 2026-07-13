package com.example.seanimetv.data.anilist

object AniListQueries {
    val VIEWER_QUERY = """
        query Viewer { Viewer { id name avatar { medium } } }
    """.trimIndent()

    val MEDIA_LIST_COLLECTION_QUERY = """
        query MediaListCollection(${'$'}userId: Int, ${'$'}type: MediaType, ${'$'}status: MediaListStatus) {
          MediaListCollection(userId: ${'$'}userId, type: ${'$'}type, status: ${'$'}status) {
            lists { entries {
              id progress status score
              media { id title { romaji english } coverImage { large extraLarge } bannerImage episodes duration genres format averageScore isAdult nextAiringEpisode { airingAt episode } }
            }}
          }
        }
    """.trimIndent()

    val MEDIA_BY_ID_QUERY = """
        query MediaById(${'$'}id: Int) {
          Media(id: ${'$'}id) {
            id idMal title { romaji english } description episodes status format isAdult
            coverImage { large extraLarge } bannerImage genres averageScore meanScore
            season seasonYear startDate { year month }
            studios { edges { isMain node { name } } }
            rankings { rank type allTime season year }
            mediaListEntry { id progress status score repeat startedAt { year month day } completedAt { year month day } }
            nextAiringEpisode { airingAt episode }
            characters(perPage: 10, sort: [ROLE, RELEVANCE]) {
              edges { role node { id name { full } image { medium } } }
            }
            relations { edges { relationType node { id title { romaji english } coverImage { large } format } } }
            recommendations(perPage: 8) {
              nodes { rating mediaRecommendation { id title { romaji english } coverImage { large } averageScore season seasonYear startDate { year } } }
            }
            streamingEpisodes { title thumbnail url site }
            trailer { site id }
          }
        }
    """.trimIndent()

    val SAVE_MEDIA_LIST_ENTRY_MUTATION = """
        mutation SaveMediaListEntry(${'$'}mediaId: Int, ${'$'}progress: Int, ${'$'}status: MediaListStatus) {
          SaveMediaListEntry(mediaId: ${'$'}mediaId, progress: ${'$'}progress, status: ${'$'}status) { id }
        }
    """.trimIndent()

    val SAVE_MEDIA_LIST_ENTRY_DETAILS_MUTATION = """
        mutation SaveMediaListEntryDetails(${'$'}mediaId: Int, ${'$'}status: MediaListStatus, ${'$'}score: Float, ${'$'}progress: Int, ${'$'}repeat: Int, ${'$'}startedAt: FuzzyDateInput, ${'$'}completedAt: FuzzyDateInput) {
          SaveMediaListEntry(mediaId: ${'$'}mediaId, status: ${'$'}status, score: ${'$'}score, progress: ${'$'}progress, repeat: ${'$'}repeat, startedAt: ${'$'}startedAt, completedAt: ${'$'}completedAt) { id }
        }
    """.trimIndent()

    val DELETE_MEDIA_LIST_ENTRY_MUTATION = """
        mutation DeleteMediaListEntry(${'$'}id: Int) {
          DeleteMediaListEntry(id: ${'$'}id) { deleted }
        }
    """.trimIndent()

    val BROWSE_MEDIA_QUERY = """
        query BrowseMedia(${'$'}page: Int, ${'$'}perPage: Int, ${'$'}sort: [MediaSort], ${'$'}genre: String, ${'$'}seasonYear: Int, ${'$'}season: MediaSeason, ${'$'}format: MediaFormat, ${'$'}status: MediaStatus) {
          Page(page: ${'$'}page, perPage: ${'$'}perPage) {
            media(sort: ${'$'}sort, genre: ${'$'}genre, seasonYear: ${'$'}seasonYear, season: ${'$'}season, format: ${'$'}format, status: ${'$'}status, type: ANIME) {
              id title { romaji english } coverImage { large extraLarge } bannerImage averageScore episodes status format isAdult
            }
          }
        }
    """.trimIndent()

    val SEARCH_MEDIA_QUERY = """
        query SearchMedia(${'$'}search: String, ${'$'}page: Int, ${'$'}perPage: Int) {
          Page(page: ${'$'}page, perPage: ${'$'}perPage) {
            media(search: ${'$'}search, type: ANIME, sort: [SEARCH_MATCH]) {
              id title { romaji english } coverImage { large extraLarge } averageScore episodes status format isAdult
            }
          }
        }
    """.trimIndent()

    val AIRING_SCHEDULE_QUERY = """
        query AiringSchedule(${'$'}airingAt_greater: Int, ${'$'}airingAt_lesser: Int, ${'$'}page: Int) {
          Page(page: ${'$'}page, perPage: 100) {
            airingSchedules(airingAt_greater: ${'$'}airingAt_greater, airingAt_lesser: ${'$'}airingAt_lesser, sort: TIME) {
              id airingAt timeUntilAiring episode
              media { id title { romaji english } coverImage { large } }
            }
          }
        }
    """.trimIndent()

    // Targeted query for the user's collection — uses mediaId_in so it returns
    // complete monthly data without hitting global pagination limits.
    val COLLECTION_AIRING_SCHEDULE_QUERY = """
        query CollectionAiringSchedule(${'$'}mediaId_in: [Int], ${'$'}airingAt_greater: Int, ${'$'}airingAt_lesser: Int, ${'$'}page: Int) {
          Page(page: ${'$'}page, perPage: 50) {
            airingSchedules(
              mediaId_in: ${'$'}mediaId_in,
              airingAt_greater: ${'$'}airingAt_greater,
              airingAt_lesser: ${'$'}airingAt_lesser,
              sort: TIME
            ) {
              id airingAt timeUntilAiring episode
              media { id title { romaji english } coverImage { large } episodes status }
            }
          }
        }
    """.trimIndent()
}
