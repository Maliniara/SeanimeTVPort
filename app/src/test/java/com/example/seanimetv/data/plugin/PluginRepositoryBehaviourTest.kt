package com.example.seanimetv.data.plugin

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies [PluginRepository] behaviour contract via [FakePluginRepository].
 *
 * These tests run on the JVM without Android dependencies. They validate the
 * business logic that both [FakePluginRepository] and [PluginRepositoryImpl] must
 * implement — DataStore-specific persistence is covered by instrumentation tests.
 */
class PluginRepositoryBehaviourTest {

    private lateinit var repository: FakePluginRepository

    @Before
    fun setUp() {
        repository = FakePluginRepository()
    }

    // --- getInstalledPlugins

    @Test
    fun getInstalledPlugins_includesBuiltin() = runTest {
        val plugins = repository.getInstalledPlugins()
        val builtin = plugins.find { it.id == "multistream-en" }
        assertNotNull("Built-in plugin must always be present", builtin)
        assertEquals("plugins/multistream-en.js", builtin!!.assetPath)
        assertNull("Built-in plugin has no remote URL", builtin.remoteUrl)
    }

    @Test
    fun getInstalledPlugins_builtinIsFirst() = runTest {
        repository.addPlugin("https://example.com/extra.js")
        val plugins = repository.getInstalledPlugins()
        assertEquals("Built-in must be the first entry", "multistream-en", plugins.first().id)
    }

    // --- addPlugin

    @Test
    fun addPlugin_persists() = runTest {
        val url = "https://example.com/my-plugin.js"
        val result = repository.addPlugin(url)
        assertTrue("addPlugin must succeed for valid HTTPS .js URL", result.isSuccess)
        val plugins = repository.getInstalledPlugins()
        assertTrue(
            "Added plugin must appear in installed list",
            plugins.any { it.remoteUrl == url }
        )
    }

    @Test
    fun addPlugin_rejectsHttpUrl() = runTest {
        val result = repository.addPlugin("http://example.com/plugin.js")
        assertTrue("Non-HTTPS URL must be rejected", result.isFailure)
    }

    @Test
    fun addPlugin_rejectsNonJsUrl() = runTest {
        val result = repository.addPlugin("https://example.com/plugin.txt")
        assertTrue("URL not ending in .js must be rejected", result.isFailure)
    }

    // --- removePlugin

    @Test
    fun removePlugin_builtinProtected() = runTest {
        repository.removePlugin("multistream-en")
        val plugins = repository.getInstalledPlugins()
        assertTrue(
            "Built-in plugin must survive remove attempt",
            plugins.any { it.id == "multistream-en" }
        )
    }

    @Test
    fun removePlugin_removesUserPlugin() = runTest {
        val url = "https://example.com/removable.js"
        repository.addPlugin(url)
        val added = repository.getInstalledPlugins().first { it.remoteUrl == url }
        repository.removePlugin(added.id)
        val plugins = repository.getInstalledPlugins()
        assertTrue(
            "User plugin must be removed from installed list",
            plugins.none { it.id == added.id }
        )
    }

    // --- getActivePlugin / setActivePlugin

    @Test
    fun getActivePlugin_defaultsToBuiltin() = runTest {
        val active = repository.getActivePlugin()
        assertNotNull("getActivePlugin must not return null when no plugin is set", active)
        assertEquals(
            "Default active plugin must be the built-in",
            "multistream-en",
            active!!.id
        )
    }

    @Test
    fun setActivePlugin_changesActivePlugin() = runTest {
        val url = "https://example.com/other.js"
        repository.addPlugin(url)
        val added = repository.getInstalledPlugins().first { it.remoteUrl == url }
        repository.setActivePlugin(added.id)
        val active = repository.getActivePlugin()
        assertEquals(
            "setActivePlugin must update the returned active plugin",
            added.id,
            active?.id
        )
    }
}
