package com.example.seanimetv.data.model

import com.example.seanimetv.data.anilist.model.AniListMedia
import com.example.seanimetv.data.anilist.model.MediaListStatus
import com.example.seanimetv.data.plugin.model.EpisodeServer
import com.example.seanimetv.data.plugin.model.Subtitle
import com.example.seanimetv.data.plugin.model.VideoSource
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataModelTest {

    @Test
    fun animeEntry_storesAllFieldsCorrectly() {
        val entry = AnimeEntry(
            id = 42,
            title = "Cowboy Bebop",
            coverImageUrl = "http://example.com/cover.jpg"
        )
        assertEquals(42, entry.id)
        assertEquals("Cowboy Bebop", entry.title)
        assertEquals("http://example.com/cover.jpg", entry.coverImageUrl)
    }

    @Test
    fun aniListMedia_deserializesFromJson_withAllFields() {
        val json = """
            {
              "id": 1,
              "title": {"romaji": "Berserk", "english": "Berserk"},
              "coverImage": {"large": "http://cover.jpg", "extraLarge": "http://coverlarge.jpg"},
              "episodes": 25,
              "status": "FINISHED",
              "description": "A dark fantasy anime",
              "genres": ["Action", "Drama"],
              "averageScore": 85,
              "bannerImage": "http://banner.jpg"
            }
        """.trimIndent()
        val media = Json.decodeFromString<AniListMedia>(json)
        assertEquals(1, media.id)
        assertEquals("Berserk", media.title?.romaji)
        assertEquals("Berserk", media.title?.english)
        assertEquals("http://cover.jpg", media.coverImage?.large)
        assertEquals("http://coverlarge.jpg", media.coverImage?.extraLarge)
        assertEquals(25, media.episodes)
        assertEquals("FINISHED", media.status)
        assertEquals("A dark fantasy anime", media.description)
        assertEquals(listOf("Action", "Drama"), media.genres)
        assertEquals(85, media.averageScore)
        assertEquals("http://banner.jpg", media.bannerImage)
    }

    @Test
    fun aniListMedia_deserializesFromJson_withOnlyRequiredFields() {
        val json = """{"id": 42}"""
        val media = Json.decodeFromString<AniListMedia>(json)
        assertEquals(42, media.id)
        assertNull(media.title)
        assertNull(media.coverImage)
        assertNull(media.episodes)
        assertEquals(emptyList<String>(), media.genres)
    }

    @Test
    fun mediaListStatus_enumValues_matchAniListApiStrings() {
        assertEquals("CURRENT", MediaListStatus.CURRENT.name)
        assertEquals("PLANNING", MediaListStatus.PLANNING.name)
        assertEquals("COMPLETED", MediaListStatus.COMPLETED.name)
        assertEquals("DROPPED", MediaListStatus.DROPPED.name)
        assertEquals("PAUSED", MediaListStatus.PAUSED.name)
        assertEquals("REPEATING", MediaListStatus.REPEATING.name)
        assertEquals(6, MediaListStatus.values().size)
    }

    @Test
    fun episodeServer_roundTripsJson_preservingAllFields() {
        val server = EpisodeServer(
            server = "TestServer",
            headers = mapOf("Authorization" to "Bearer token123"),
            videoSources = listOf(
                VideoSource(
                    url = "http://stream.m3u8",
                    type = "m3u8",
                    quality = "1080p",
                    subtitles = listOf(
                        Subtitle(id = "sub1", url = "http://sub.vtt", language = "English", isDefault = true)
                    )
                )
            )
        )
        val jsonStr = Json.encodeToString(server)
        val decoded = Json.decodeFromString<EpisodeServer>(jsonStr)
        assertEquals(server.server, decoded.server)
        assertEquals(server.headers, decoded.headers)
        assertEquals(1, decoded.videoSources.size)
        assertEquals("http://stream.m3u8", decoded.videoSources[0].url)
        assertEquals("m3u8", decoded.videoSources[0].type)
        assertEquals("1080p", decoded.videoSources[0].quality)
        assertEquals(1, decoded.videoSources[0].subtitles.size)
        assertEquals("English", decoded.videoSources[0].subtitles[0].language)
        assertTrue(decoded.videoSources[0].subtitles[0].isDefault)
    }

    @Test
    fun episodeServer_withEmptyCollections_constructsCorrectly() {
        val server = EpisodeServer(server = "EmptyServer", headers = emptyMap(), videoSources = emptyList())
        assertEquals("EmptyServer", server.server)
        assertEquals(emptyMap<String, String>(), server.headers)
        assertEquals(emptyList<VideoSource>(), server.videoSources)
    }
}
