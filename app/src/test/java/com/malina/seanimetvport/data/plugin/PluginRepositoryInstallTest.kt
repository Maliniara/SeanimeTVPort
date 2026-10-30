package com.malina.seanimetvport.data.plugin

import com.malina.seanimetvport.data.plugin.store.PluginStoreEntry
import com.malina.seanimetvport.data.plugin.store.PluginStoreRepositoryImpl
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for [PluginRepository.installPlugin] (via [FakePluginRepository]) and
 * [PluginStoreRepositoryImpl.fetchCatalogue] failure propagation.
 *
 * All tests run on the JVM without Android dependencies.
 */
class PluginRepositoryInstallTest {

    private val sampleEntry = PluginStoreEntry(
        id = "test-plugin",
        name = "Test Plugin",
        version = "1.2.3",
        description = "A test plugin",
        author = "test-author",
        iconUrl = null,
        scriptUrl = "https://example.com/test-plugin.js"
    )

    /**
     * installPlugin_mapsAllFieldsCorrectly — each field on the returned [PluginEntry] must
     * be sourced from the corresponding field on [PluginStoreEntry]:
     *   id → id, name → name, assetPath → null, scriptUrl → remoteUrl,
     *   version → version, description → description
     */
    @Test
    fun installPlugin_mapsAllFieldsCorrectly() = runTest {
        val repository = FakePluginRepository()

        val result = repository.installPlugin(sampleEntry)

        assertTrue("installPlugin must return success for a valid store entry", result.isSuccess)
        val entry = result.getOrThrow()
        assertEquals(sampleEntry.id, entry.id)
        assertEquals(sampleEntry.name, entry.name)
        assertNull("assetPath must be null for store-installed plugins", entry.assetPath)
        assertEquals(sampleEntry.scriptUrl, entry.remoteUrl)
        assertEquals(sampleEntry.version, entry.version)
        assertEquals(sampleEntry.description, entry.description)
    }

    /**
     * installPlugin_isIdempotent — calling installPlugin twice with the same entry.id
     * must not create a duplicate; the list contains exactly one entry with that id,
     * and the second call returns [Result.success].
     */
    @Test
    fun installPlugin_isIdempotent() = runTest {
        val repository = FakePluginRepository()

        repository.installPlugin(sampleEntry)
        val secondResult = repository.installPlugin(sampleEntry)

        assertTrue("Second installPlugin call must return success", secondResult.isSuccess)
        val installedCount = repository.getInstalledPlugins().count { it.id == sampleEntry.id }
        assertEquals("List must contain exactly one entry for the plugin id after two installs", 1, installedCount)
    }

    /**
     * installPlugin_builtinReturnsFailure — attempting to install the built-in plugin
     * (id = "multistream-en") must return [Result.failure]; the in-memory list must
     * not gain an extra entry for that id.
     */
    @Test
    fun installPlugin_builtinReturnsFailure() = runTest {
        val builtinEntry = PluginStoreEntry(
            id = "multistream-en",
            name = "Multistream EN",
            version = "1.0.0",
            description = "Built-in plugin",
            author = "system",
            scriptUrl = "https://example.com/multistream-en.js"
        )
        val repository = FakePluginRepository()

        val result = repository.installPlugin(builtinEntry)

        assertTrue("Installing built-in plugin must return failure", result.isFailure)
        // The installed list must still contain exactly one "multistream-en" (the built-in itself)
        val builtinCount = repository.getInstalledPlugins().count { it.id == "multistream-en" }
        assertEquals("No duplicate multistream-en entry must be created", 1, builtinCount)
    }

    /**
     * fetchCatalogue_propagatesNetworkExceptionAsFailure — when the underlying OkHttp call
     * throws (e.g. network failure), [PluginStoreRepositoryImpl.fetchCatalogue] must return
     * [Result.failure] rather than propagating the exception to the caller.
     */
    @Test
    fun fetchCatalogue_propagatesNetworkExceptionAsFailure() = runTest {
        val throwingClient = OkHttpClient.Builder()
            .addInterceptor { throw IOException("Simulated network failure") }
            .build()
        val storeRepository = PluginStoreRepositoryImpl(throwingClient)

        val result = storeRepository.fetchCatalogue()

        assertTrue(
            "fetchCatalogue must return Result.failure when the OkHttp call throws",
            result.isFailure
        )
    }
}
