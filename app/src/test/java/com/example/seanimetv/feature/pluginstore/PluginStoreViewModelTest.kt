package com.example.seanimetv.feature.pluginstore

import com.example.seanimetv.data.plugin.FakePluginRepository
import com.example.seanimetv.data.plugin.model.PluginEntry
import com.example.seanimetv.data.plugin.store.PluginStoreEntry
import com.example.seanimetv.data.plugin.store.PluginStoreManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PluginStoreViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val fakeEntry = PluginStoreEntry(
        id = "test-plugin",
        name = "Test Plugin",
        version = "1.0.0",
        description = "A test plugin",
        author = "Test Author",
        scriptUrl = "https://example.com/test-plugin.js"
    )

    private val fakeManifest = PluginStoreManifest(listOf(fakeEntry))

    private fun makeViewModel(
        storeRepository: FakePluginStoreRepository = FakePluginStoreRepository(),
        pluginRepository: FakePluginRepository = FakePluginRepository()
    ) = PluginStoreViewModel(storeRepository, pluginRepository)

    /**
     * Verifies that loadCatalogue() transitions from Loading to Loaded on success and that
     * Loaded.installedIds equals the set of ids returned by the plugin repository.
     */
    @Test
    fun loadCatalogue_success_transitionsToLoadedWithCorrectInstalledIds() = runTest {
        val fakePlugin = FakePluginRepository(
            initialUserPlugins = listOf(
                PluginEntry(
                    id = "user-plugin",
                    name = "User Plugin",
                    assetPath = null,
                    remoteUrl = "https://example.com/user-plugin.js"
                )
            )
        )
        val fakeStore = FakePluginStoreRepository().apply {
            result = Result.success(fakeManifest)
        }
        val vm = makeViewModel(storeRepository = fakeStore, pluginRepository = fakePlugin)

        vm.loadCatalogue()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("State should be Loaded after successful catalogue fetch", state is PluginStoreUiState.Loaded)
        val loaded = state as PluginStoreUiState.Loaded
        val expectedIds = fakePlugin.getInstalledPlugins().map { it.id }.toSet()
        assertEquals(
            "installedIds should equal the set of ids from the installed plugin list",
            expectedIds,
            loaded.installedIds
        )
    }

    /**
     * Verifies that loadCatalogue() transitions to Error when fetchCatalogue returns failure
     * and that Error.message is non-empty.
     */
    @Test
    fun loadCatalogue_failure_transitionsToErrorWithNonEmptyMessage() = runTest {
        val fakeStore = FakePluginStoreRepository().apply {
            result = Result.failure(RuntimeException("Network error"))
        }
        val vm = makeViewModel(storeRepository = fakeStore)

        vm.loadCatalogue()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("State should be Error when fetchCatalogue returns failure", state is PluginStoreUiState.Error)
        assertTrue(
            "Error.message should be non-empty",
            (state as PluginStoreUiState.Error).message.isNotEmpty()
        )
    }

    /**
     * Verifies that installPlugin() when state is Loaded calls pluginRepository.installPlugin
     * and entry.id appears in installedIds of the updated state.
     */
    @Test
    fun installPlugin_whenLoaded_callsRepositoryAndUpdatesInstalledIds() = runTest {
        val fakeStore = FakePluginStoreRepository().apply {
            result = Result.success(fakeManifest)
        }
        val fakePlugin = FakePluginRepository()
        val vm = makeViewModel(storeRepository = fakeStore, pluginRepository = fakePlugin)
        vm.loadCatalogue()
        advanceUntilIdle()

        vm.installPlugin(fakeEntry)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("State should remain Loaded after install", state is PluginStoreUiState.Loaded)
        assertTrue(
            "entry.id should appear in installedIds after installPlugin succeeds",
            (state as PluginStoreUiState.Loaded).installedIds.contains(fakeEntry.id)
        )
    }

    /**
     * Verifies that installPlugin() when state is Loading keeps state as Loading
     * and makes no call to pluginRepository.installPlugin.
     */
    @Test
    fun installPlugin_whenLoading_stateRemainsLoadingAndNoRepositoryCallIsMade() = runTest {
        val fakePlugin = FakePluginRepository()
        val vm = makeViewModel(pluginRepository = fakePlugin)
        // Do NOT call loadCatalogue() — ViewModel stays in Loading state

        vm.installPlugin(fakeEntry)
        advanceUntilIdle()

        assertTrue(
            "State should remain Loading when installPlugin is called before loadCatalogue",
            vm.uiState.value is PluginStoreUiState.Loading
        )
        assertFalse(
            "pluginRepository.installPlugin should not be called when state is Loading",
            fakePlugin.getInstalledPlugins().any { it.id == fakeEntry.id }
        )
    }

    /**
     * Verifies that calling loadCatalogue() from an already-Loaded state resets the state
     * to Loading first (before the network fetch completes) and then returns to Loaded on
     * success.  This guards against the UI skipping the loading indicator on refresh.
     */
    @Test
    fun loadCatalogue_whenAlreadyLoaded_transitionsBackToLoadingThenSucceeds() = runTest {
        val fakeStore = FakePluginStoreRepository().apply {
            result = Result.success(fakeManifest)
        }
        val vm = makeViewModel(storeRepository = fakeStore)

        // First load — reach Loaded state
        vm.loadCatalogue()
        advanceUntilIdle()
        assertTrue("Precondition: state must be Loaded before reload", vm.uiState.value is PluginStoreUiState.Loaded)

        // Collect all state emissions during the second load. Unconfined so the
        // collector resumes at EACH emission — StateFlow conflates the transient
        // Loading away from a StandardTestDispatcher collector.
        val observedStates = mutableListOf<PluginStoreUiState>()
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect { observedStates.add(it) }
        }

        vm.loadCatalogue()
        advanceUntilIdle()
        collectJob.cancel()

        assertTrue(
            "loadCatalogue from Loaded must emit a Loading state before completing",
            observedStates.any { it is PluginStoreUiState.Loading }
        )
        assertTrue(
            "loadCatalogue must end in Loaded state when fetch succeeds",
            vm.uiState.value is PluginStoreUiState.Loaded
        )
    }

    /**
     * Verifies that when pluginRepository.installPlugin returns Result.failure the ViewModel
     * silently ignores the failure: state remains Loaded and the entry's id does NOT appear
     * in installedIds.  This is the "silent failure" path in PluginStoreViewModel.installPlugin.
     */
    @Test
    fun installPlugin_whenRepositoryReturnsFailure_installedIdsRemainUnchanged() = runTest {
        val fakeStore = FakePluginStoreRepository().apply {
            result = Result.success(fakeManifest)
        }
        val fakePlugin = FakePluginRepository(forceInstallFailure = true)
        val vm = makeViewModel(storeRepository = fakeStore, pluginRepository = fakePlugin)
        vm.loadCatalogue()
        advanceUntilIdle()

        val installedIdsBefore = (vm.uiState.value as PluginStoreUiState.Loaded).installedIds

        vm.installPlugin(fakeEntry)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("State must remain Loaded after a failed install", state is PluginStoreUiState.Loaded)
        val loadedState = state as PluginStoreUiState.Loaded
        assertFalse(
            "entry.id must not be added to installedIds when installPlugin returns failure",
            loadedState.installedIds.contains(fakeEntry.id)
        )
        assertEquals(
            "installedIds must be identical to pre-install state when installPlugin returns failure",
            installedIdsBefore,
            loadedState.installedIds
        )
    }
}
