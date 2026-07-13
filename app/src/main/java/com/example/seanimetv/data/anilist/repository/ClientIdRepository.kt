package com.example.seanimetv.data.anilist.repository

import android.content.SharedPreferences

interface ClientIdRepository {
    fun getClientId(): String?
    fun saveClientId(id: String)
    fun getClientSecret(): String?
    fun saveClientSecret(secret: String)
}

class ClientIdPrefs(private val prefs: SharedPreferences) : ClientIdRepository {
    override fun getClientId(): String? =
        prefs.getString("anilist_client_id", null)?.trim()?.takeIf { it.isNotBlank() }

    override fun saveClientId(id: String) {
        prefs.edit().putString("anilist_client_id", id.trim()).apply()
    }

    override fun getClientSecret(): String? =
        prefs.getString("anilist_client_secret", null)?.trim()?.takeIf { it.isNotBlank() }

    override fun saveClientSecret(secret: String) {
        prefs.edit().putString("anilist_client_secret", secret.trim()).apply()
    }
}
