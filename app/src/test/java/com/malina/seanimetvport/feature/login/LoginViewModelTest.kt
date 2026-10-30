package com.malina.seanimetvport.feature.login

import com.malina.seanimetvport.data.anilist.FakeAuthRepository
import com.malina.seanimetvport.data.anilist.AniListConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun initialState_isIdle() {
        val vm = LoginViewModel(FakeAuthRepository())
        assertEquals(LoginUiState.Idle, vm.uiState.value)
    }

    @Test
    fun buildOAuthUrl_containsClientIdAndPinRedirect() {
        val vm = LoginViewModel(FakeAuthRepository())
        val url = vm.buildOAuthUrl()
        assertTrue(url.contains("client_id=${AniListConfig.CLIENT_ID}"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("oauth%2Fpin"))
    }

    @Test
    fun onError_emitsError() = runTest {
        val vm = LoginViewModel(FakeAuthRepository())
        vm.onError("something went wrong")
        assertEquals(LoginUiState.Error("something went wrong"), vm.uiState.value)
    }

    @Test
    fun retry_resetsToIdle() = runTest {
        val vm = LoginViewModel(FakeAuthRepository())
        vm.onError("some error")
        vm.retry()
        assertEquals(LoginUiState.Idle, vm.uiState.value)
    }
}
