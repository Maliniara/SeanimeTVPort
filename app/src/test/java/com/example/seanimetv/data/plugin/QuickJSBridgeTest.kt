package com.example.seanimetv.data.plugin

import com.example.seanimetv.data.anilist.model.AniListMedia
import com.example.seanimetv.data.anilist.model.AniListStartDate
import com.example.seanimetv.data.anilist.model.AniListTitle
import com.example.seanimetv.data.plugin.model.Episode
import com.example.seanimetv.data.plugin.model.SearchResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the QuickJS bridge layer: JSON encoding of search args, integer
 * serialization safety, and result parsing for Episode and SearchResult.
 *
 * All tests are pure JVM — no Android or QuickJS native dependencies.
 */
class QuickJSBridgeTest {

    // (a) encodeSearchArgs produces a full Seanime SearchOptions JSON shape
    @Test
    fun encodeSearchArgs_withMedia_producesNestedMediaObjectWithRomajiTitleAndTopLevelQueryAndDub() {
        val fakeMedia = AniListMedia(
            id = 101,
            title = AniListTitle(romaji = "Sousou no Frieren", english = "Frieren: Beyond Journey's End"),
            episodes = 28,
            status = "FINISHED",
            format = "TV",
            startDate = AniListStartDate(year = 2023, month = 9)
        )

        val result = QuickJSEngine.encodeSearchArgs("Frieren", fakeMedia, false)
        val parsed = Json.parseToJsonElement(result).jsonObject

        assertEquals("Frieren", parsed["query"]!!.jsonPrimitive.content)
        assertFalse(parsed["dub"]!!.jsonPrimitive.boolean)

        val media = parsed["media"]!!.jsonObject
        assertEquals("Sousou no Frieren", media["romajiTitle"]!!.jsonPrimitive.content)
    }

    // (b) buildJsonObject serializes Int as a JSON integer, not a quoted string
    @Test
    fun buildJsonObject_withIntValue_serializesAsIntegerNotString() {
        val obj = buildJsonObject {
            put("number", 1)
        }
        val serialized = obj.toString()
        assertTrue(
            "Expected integer serialization without quotes but got: $serialized",
            serialized.contains("\"number\":1")
        )
        assertEquals(1, obj["number"]!!.jsonPrimitive.int)
    }

    // (c) Parsing a JS episode array produces Episode with url and provider populated
    @Test
    fun parseEpisodeJson_withUrlAndProvider_producesCorrectEpisode() {
        val episodeJson = """[{"id":"x","number":1,"url":"https://ep.url","provider":"gogo"}]"""
        val episodes = Json.parseToJsonElement(episodeJson).jsonArray.map { el ->
            val obj = el.jsonObject
            Episode(
                id = obj["id"]!!.jsonPrimitive.content,
                number = obj["number"]!!.jsonPrimitive.int,
                title = obj["title"]?.jsonPrimitive?.contentOrNull,
                thumbnail = obj["thumbnail"]?.jsonPrimitive?.contentOrNull,
                url = obj["url"]?.jsonPrimitive?.contentOrNull,
                provider = obj["provider"]?.jsonPrimitive?.contentOrNull
            )
        }

        assertEquals(1, episodes.size)
        val ep = episodes[0]
        assertEquals("x", ep.id)
        assertEquals(1, ep.number)
        assertEquals("https://ep.url", ep.url)
        assertEquals("gogo", ep.provider)
    }

    // (e) encodeSearchArgs with null media does not throw and produces a well-formed JSON object
    //     with fallback id == 0 and null optional string fields (gap (c) from spec review)
    @Test
    fun encodeSearchArgs_withNullMedia_doesNotThrowAndProducesZeroIdAndNullTitleFields() {
        val result = QuickJSEngine.encodeSearchArgs("Attack on Titan", null, true)
        val parsed = Json.parseToJsonElement(result).jsonObject

        // Top-level fields
        assertEquals("Attack on Titan", parsed["query"]!!.jsonPrimitive.content)
        assertTrue(parsed["dub"]!!.jsonPrimitive.boolean)

        // media sub-object: id falls back to 0; optional string fields serialize as JSON null
        val media = parsed["media"]!!.jsonObject
        assertEquals(0, media["id"]!!.jsonPrimitive.int)
        assertNull(
            "romajiTitle must be null when no media is provided",
            media["romajiTitle"]!!.jsonPrimitive.contentOrNull
        )
        assertNull(
            "englishTitle must be null when no media is provided",
            media["englishTitle"]!!.jsonPrimitive.contentOrNull
        )
    }

    // (d) Parsing a JS search result array produces SearchResult with url and subOrDub populated
    @Test
    fun parseSearchResultJson_withUrlAndSubOrDub_producesCorrectSearchResult() {
        val searchJson = """[{"id":"x","title":"T","url":"https://s.url","subOrDub":"sub"}]"""
        val results = Json.parseToJsonElement(searchJson).jsonArray.map { el ->
            val obj = el.jsonObject
            SearchResult(
                id = obj["id"]!!.jsonPrimitive.content,
                title = obj["title"]!!.jsonPrimitive.content,
                url = obj["url"]?.jsonPrimitive?.contentOrNull,
                subOrDub = obj["subOrDub"]?.jsonPrimitive?.contentOrNull
            )
        }

        assertEquals(1, results.size)
        val sr = results[0]
        assertEquals("x", sr.id)
        assertEquals("T", sr.title)
        assertEquals("https://s.url", sr.url)
        assertEquals("sub", sr.subOrDub)
    }
}
