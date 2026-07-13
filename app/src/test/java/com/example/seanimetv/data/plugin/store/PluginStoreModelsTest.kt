package com.example.seanimetv.data.plugin.store

import com.example.seanimetv.data.plugin.model.PluginEntry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PluginStoreModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun pluginStoreEntry_deserializesFromCamelCaseJson_withNullIconUrlAndEmptyTags() {
        val jsonStr = """
            {
              "id": "test-plugin",
              "name": "Test Plugin",
              "version": "1.0.0",
              "description": "A test plugin",
              "author": "test-author",
              "iconUrl": null,
              "scriptUrl": "https://example.com/plugin.js",
              "tags": []
            }
        """.trimIndent()
        val entry = json.decodeFromString<PluginStoreEntry>(jsonStr)
        assertEquals("test-plugin", entry.id)
        assertEquals("Test Plugin", entry.name)
        assertEquals("1.0.0", entry.version)
        assertEquals("A test plugin", entry.description)
        assertEquals("test-author", entry.author)
        assertNull(entry.iconUrl)
        assertEquals("https://example.com/plugin.js", entry.scriptUrl)
        assertEquals(emptyList<String>(), entry.tags)
    }

    @Test
    fun pluginEntry_withoutVersionAndDescriptionKeys_deserializesToNullForBothFields() {
        val jsonStr = """{"id": "builtin", "name": "Built-in", "assetPath": "plugins/builtin.js"}"""
        val entry = json.decodeFromString<PluginEntry>(jsonStr)
        assertEquals("builtin", entry.id)
        assertNull(entry.version)
        assertNull(entry.description)
    }

    /**
     * pluginEntry_withExactStoredDataStoreFormat_deserializesAllFieldsCorrectly
     *
     * Verifies that the exact JSON format persisted by DataStore
     * — {"id":"...","name":"...","assetPath":null,"remoteUrl":"..."} —
     * round-trips correctly, including the explicit null for assetPath and a
     * non-null remoteUrl.  This guards against backward-compat regressions when
     * the PluginEntry schema evolves.
     */
    @Test
    fun pluginEntry_withExactStoredDataStoreFormat_deserializesAllFieldsCorrectly() {
        val jsonStr = """{"id":"some-plugin","name":"Some Plugin","assetPath":null,"remoteUrl":"https://example.com/some-plugin.js"}"""
        val entry = json.decodeFromString<PluginEntry>(jsonStr)
        assertEquals("some-plugin", entry.id)
        assertEquals("Some Plugin", entry.name)
        assertNull("assetPath must be null when stored as JSON null", entry.assetPath)
        assertEquals("https://example.com/some-plugin.js", entry.remoteUrl)
        assertNull("version must be null when absent from stored JSON", entry.version)
        assertNull("description must be null when absent from stored JSON", entry.description)
    }

    /**
     * pluginEntry_roundTripSerializationPreservesAllFields
     *
     * Verifies that encoding a fully populated PluginEntry to JSON and decoding
     * it again produces an equal object.  This ensures serialization correctness
     * for DataStore persistence: no fields are silently lost or coerced.
     */
    @Test
    fun pluginEntry_roundTripSerializationPreservesAllFields() {
        val roundTripJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val original = PluginEntry(
            id = "round-trip-plugin",
            name = "Round Trip Plugin",
            assetPath = null,
            remoteUrl = "https://example.com/round-trip.js",
            version = "2.0.0",
            description = "Round trip test"
        )
        val serialized = roundTripJson.encodeToString(original)
        val restored = roundTripJson.decodeFromString<PluginEntry>(serialized)
        assertEquals("All fields must survive a JSON encode → decode round-trip", original, restored)
    }
}
