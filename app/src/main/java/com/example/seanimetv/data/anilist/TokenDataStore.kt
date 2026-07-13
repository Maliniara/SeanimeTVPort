package com.example.seanimetv.data.anilist

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.seanimetv.data.anilist.repository.AuthRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.aniListDataStore: DataStore<Preferences> by preferencesDataStore(name = "anilist_prefs")

class TokenDataStore(private val context: Context) : AuthRepository {

    private val TOKEN_KEY = stringPreferencesKey("anilist_token")

    // The auth interceptor reads the token on EVERY request (via runBlocking on
    // an OkHttp thread). The token only changes at login/logout, so cache it in
    // memory after the first DataStore read instead of hitting disk per call.
    @Volatile
    private var cachedToken: String? = null
    @Volatile
    private var cacheLoaded = false

    override suspend fun getToken(): String? {
        if (cacheLoaded) return cachedToken
        val token = context.aniListDataStore.data.map { it[TOKEN_KEY] }.first()
        cachedToken = token
        cacheLoaded = true
        return token
    }

    override suspend fun saveToken(token: String) {
        context.aniListDataStore.edit { it[TOKEN_KEY] = token }
        cachedToken = token
        cacheLoaded = true
    }

    override suspend fun clearToken() {
        context.aniListDataStore.edit { it.remove(TOKEN_KEY) }
        cachedToken = null
        cacheLoaded = true
    }
}
