package com.malina.seanimetvport.data.anilist

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import com.malina.seanimetvport.data.anilist.repository.AuthRepository

internal class TokenPreferencesMigration(
    private val keys: List<Preferences.Key<String>>,
    private val cipher: TokenCipher = TokenCipher()
) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        keys.any { key -> currentData[key]?.let { !cipher.isEncrypted(it) } == true }

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply {
            keys.forEach { key ->
                this[key]?.takeUnless(cipher::isEncrypted)?.let { this[key] = cipher.encrypt(it) }
            }
        }

    override suspend fun cleanUp() = Unit
}

/** DataStore calls cleanup only after the destination has been persisted successfully. */
internal class LegacyTokenMigration(
    private val legacy: AuthRepository,
    private val activeTokenKey: Preferences.Key<String>,
    private val cipher: TokenCipher = TokenCipher()
) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean = legacy.getToken() != null

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply {
            if (this[activeTokenKey] == null) {
                legacy.getToken()?.let { this[activeTokenKey] = cipher.encrypt(it) }
            }
        }

    override suspend fun cleanUp() = legacy.clearToken()
}
