package com.malina.seanimetvport.feature.settings

import com.malina.seanimetvport.data.anilist.AniListAccount
import com.malina.seanimetvport.data.anilist.FakeAccountRepository
import com.malina.seanimetvport.data.anilist.FakeAuthRepository
import com.malina.seanimetvport.data.anilist.repository.FakeAniListRepository
import com.malina.seanimetvport.data.settings.FakeAnimeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeViewModel(
        fakeAuth: FakeAuthRepository = FakeAuthRepository(initial = "token"),
        fakeAniList: FakeAniListRepository = FakeAniListRepository(),
        fakeAnime: FakeAnimeSettingsRepository = FakeAnimeSettingsRepository(),
        fakeAccounts: FakeAccountRepository = FakeAccountRepository(),
        onLoggedOut: () -> Unit = {}
    ) = SettingsViewModel(
        authRepository = fakeAuth,
        aniListRepository = fakeAniList,
        animeSettingsStore = fakeAnime,
        accountRepository = fakeAccounts,
        onLoggedOut = onLoggedOut
    )

    /** Logging out of the last account clears its token and signals LoggedOut. */
    @Test
    fun logout_lastAccount_clearsAndSignalsLoggedOut() = runTest {
        val fakeAccounts = FakeAccountRepository(
            initialAccounts = listOf(AniListAccount(id = 1, name = "u", token = "tok"))
        )
        val vm = makeViewModel(fakeAccounts = fakeAccounts)
        advanceUntilIdle()

        vm.logout()
        advanceUntilIdle()

        assertNull("Token should be null after logging out of the last account", fakeAccounts.getToken())
        assertEquals(SettingsUiState.LoggedOut, vm.uiState.value)
    }

    /** addGuest inserts the local Guest account and makes it active. */
    @Test
    fun addGuest_addsAndActivatesGuest() = runTest {
        val fakeAccounts = FakeAccountRepository(
            initialAccounts = listOf(AniListAccount(id = 1, name = "u", token = "tok"))
        )
        val vm = makeViewModel(fakeAccounts = fakeAccounts)
        advanceUntilIdle()

        vm.addGuest()
        advanceUntilIdle()

        assertTrue(
            "Guest account should be present after addGuest",
            fakeAccounts.listAccounts().any { it.isGuest }
        )
        assertEquals(AniListAccount.GUEST_ID, fakeAccounts.activeAccountId.first())
    }

    /** Toggling an anime preference surfaces via the animeSettings flow. */
    @Test
    fun setHideAudienceScore_updatesAnimeSettings() = runTest {
        val vm = makeViewModel()
        val job = launch { vm.animeSettings.collect {} }
        advanceUntilIdle()

        vm.setHideAudienceScore(true)
        advanceUntilIdle()

        assertTrue(
            "hideAudienceScore should be true after toggle",
            vm.animeSettings.value.hideAudienceScore
        )
        job.cancel()
    }
}
