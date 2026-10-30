package com.malina.seanimetvport.data.plugin

import com.malina.seanimetvport.data.plugin.model.Episode
import com.malina.seanimetvport.data.plugin.model.EpisodeServer
import com.malina.seanimetvport.data.plugin.model.SearchResult
import com.malina.seanimetvport.data.plugin.model.VideoSource
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PluginModelTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun searchResult_withAllFields_exposesUrlAndSubOrDub() {
        val result = SearchResult(id = "x", title = "T", url = "https://example.com", subOrDub = "sub")
        assertEquals("x", result.id)
        assertEquals("T", result.title)
        assertEquals("https://example.com", result.url)
        assertEquals("sub", result.subOrDub)
    }

    @Test
    fun searchResult_withRequiredFieldsOnly_hasNullUrlAndSubOrDub() {
        val result = SearchResult(id = "x", title = "T")
        assertNull(result.url)
        assertNull(result.subOrDub)
    }

    @Test
    fun episode_withAllFields_exposesUrlAndProvider() {
        val episode = Episode(id = "x", number = 1, url = "https://ep.url", provider = "gogo")
        assertEquals("x", episode.id)
        assertEquals(1, episode.number)
        assertEquals("https://ep.url", episode.url)
        assertEquals("gogo", episode.provider)
    }

    @Test
    fun episode_withRequiredFieldsOnly_hasNullUrlAndProvider() {
        val episode = Episode(id = "x", number = 1)
        assertNull(episode.url)
        assertNull(episode.provider)
    }

    @Test
    fun episodeServer_withProvider_roundTripsJsonPreservingProviderField() {
        val server = EpisodeServer(
            server = "s",
            headers = emptyMap(),
            videoSources = emptyList(),
            provider = "gogo"
        )
        val serialized = Json.encodeToString(server)
        val decoded = Json.decodeFromString<EpisodeServer>(serialized)
        assertEquals("s", decoded.server)
        assertEquals("gogo", decoded.provider)
    }

    @Test
    fun episodeServer_deserializedWithoutProviderField_hasNullProvider() {
        val jsonStr = """{"server":"s","headers":{},"videoSources":[]}"""
        val decoded = json.decodeFromString<EpisodeServer>(jsonStr)
        assertNull(decoded.provider)
    }

    @Test
    fun videoSource_withLabel_exposesLabel() {
        val source = VideoSource(
            url = "u",
            type = "hls",
            quality = "1080p",
            subtitles = emptyList(),
            label = "English"
        )
        assertEquals("u", source.url)
        assertEquals("English", source.label)
    }

    @Test
    fun videoSource_withoutLabel_hasNullLabel() {
        val source = VideoSource(url = "u", type = "hls", quality = "1080p", subtitles = emptyList())
        assertNull(source.label)
    }
}
