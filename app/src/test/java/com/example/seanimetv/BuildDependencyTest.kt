package com.example.seanimetv

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Verifies that build-configuration dependencies added in Group 1 are present
 * and functional at compile/runtime. These tests fail to compile if the
 * kotlinx-serialization-json dependency is absent, and fail at runtime if
 * the serialization plugin is not applied.
 */
class BuildDependencyTest {

    @Serializable
    data class SamplePayload(val id: Int, val label: String)

    @Test
    fun `kotlinx serialization json decodes a simple object`() {
        val raw = """{"id":1,"label":"hello"}"""
        val decoded = Json.decodeFromString<SamplePayload>(raw)
        assertEquals(1, decoded.id)
        assertEquals("hello", decoded.label)
    }

    @Test
    fun `kotlinx serialization json encodes a simple object`() {
        val payload = SamplePayload(id = 7, label = "world")
        val encoded = Json.encodeToString(SamplePayload.serializer(), payload)
        assertNotNull(encoded)
        assert(encoded.contains("\"id\":7")) { "Expected id field in JSON, got: $encoded" }
        assert(encoded.contains("\"label\":\"world\"")) { "Expected label field in JSON, got: $encoded" }
    }

    @Test
    fun `kotlinx serialization handles nullable fields with defaults`() {
        @Serializable
        data class WithDefault(val name: String, val count: Int = 0)

        val decoded = Json { ignoreUnknownKeys = true }
            .decodeFromString<WithDefault>("""{"name":"test"}""")
        assertEquals("test", decoded.name)
        assertEquals(0, decoded.count)
    }
}
