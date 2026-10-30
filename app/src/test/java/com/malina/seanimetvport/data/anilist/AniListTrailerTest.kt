package com.malina.seanimetvport.data.anilist

import com.malina.seanimetvport.data.anilist.model.AniListMedia
import com.malina.seanimetvport.data.anilist.model.AniListTrailer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private val json = Json { ignoreUnknownKeys = true }

class AniListTrailerTest {

    /**
     * trailerDeserialization_producesCorrectSiteAndId
     * A bare trailer JSON object deserializes into AniListTrailer with the expected field values.
     */
    @Test
    fun trailerDeserialization_producesCorrectSiteAndId() {
        val raw = """{"site":"youtube","id":"abc123"}"""

        val trailer = json.decodeFromString<AniListTrailer>(raw)

        assertEquals("youtube", trailer.site)
        assertEquals("abc123", trailer.id)
    }

    /**
     * mediaDeserialization_withTrailer_populatesTrailerField — media JSON that contains a trailer
     * object yields a non-null trailer with the expected site and id values.
     * mediaDeserialization_withoutTrailer_yieldsNullTrailerField — media JSON that omits the
     * trailer field entirely yields trailer == null.
     */
    @Test
    fun mediaDeserialization_trailerPresentAndAbsentCases() {
        val withTrailer = """{"id":1,"trailer":{"site":"youtube","id":"abc123"}}"""
        val withoutTrailer = """{"id":2}"""

        val mediaWith = json.decodeFromString<AniListMedia>(withTrailer)
        val mediaWithout = json.decodeFromString<AniListMedia>(withoutTrailer)

        assertEquals("youtube", mediaWith.trailer?.site)
        assertEquals("abc123", mediaWith.trailer?.id)
        assertNull(mediaWithout.trailer)
    }
}
