package com.example.seanimetv.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.seanimetv.data.anilist.AniListConfig
import com.example.seanimetv.data.anilist.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

sealed class LoginUiState {
    object Idle : LoginUiState()
    object ExchangingToken : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val okHttpClient: OkHttpClient = OkHttpClient(),
    val onSuccess: () -> Unit = {}
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun buildOAuthUrl(): String =
        "https://anilist.co/api/v2/oauth/authorize" +
            "?client_id=${AniListConfig.CLIENT_ID}" +
            "&redirect_uri=https%3A%2F%2Fanilist.co%2Fapi%2Fv2%2Foauth%2Fpin" +
            "&response_type=code"

    fun onCodeReceived(code: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.ExchangingToken
            runCatching {
                withContext(Dispatchers.IO) {
                    val body = FormBody.Builder()
                        .add("grant_type", "authorization_code")
                        .add("client_id", AniListConfig.CLIENT_ID)
                        .add("client_secret", AniListConfig.CLIENT_SECRET)
                        .add("redirect_uri", "https://anilist.co/api/v2/oauth/pin")
                        .add("code", code)
                        .build()
                    val request = Request.Builder()
                        .url("https://anilist.co/api/v2/oauth/token")
                        .post(body)
                        .header("Accept", "application/json")
                        .build()
                    okHttpClient.newCall(request).execute().use { response ->
                        JSONObject(response.body?.string() ?: "").getString("access_token")
                    }
                }
            }.onSuccess { token ->
                authRepository.saveToken(token)
                _uiState.value = LoginUiState.Idle
                onSuccess()
            }.onFailure { ex ->
                _uiState.value = LoginUiState.Error(ex.message ?: "Token exchange failed")
            }
        }
    }

    fun onError(message: String) { _uiState.value = LoginUiState.Error(message) }

    fun retry() { _uiState.value = LoginUiState.Idle }
}
