package com.example.seanimetv.data.anilist

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.seanimetv.data.anilist.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.accountDataStore: DataStore<Preferences> by preferencesDataStore(name = "account_prefs")

/**
 * One account. Normally an AniList account keyed by AniList user id; a special
 * local-only "Guest" account (see [guestAccount]) has [id] == [GUEST_ID], no
 * token and [isGuest] true — it is backed by local data only (no AniList).
 */
@Serializable
data class AniListAccount(
    val id: Int,
    val name: String,
    val avatarUrl: String? = null,
    val token: String,
    val isGuest: Boolean = false
) {
    companion object {
        /** Sentinel id for the local Guest account (never a real AniList id). */
        const val GUEST_ID = -1

        /** The single local Guest account — no AniList token, local list only. */
        fun guestAccount() = AniListAccount(
            id = GUEST_ID,
            name = "Guest",
            avatarUrl = null,
            token = "",
            isGuest = true
        )
    }
}

/**
 * Multi-account auth store. Extends [AuthRepository] so the existing API-client
 * interceptor keeps working ([getToken] returns the ACTIVE account's token); the
 * added methods manage the set of accounts and which one is active.
 */
interface AccountRepository : AuthRepository {
    val accounts: Flow<List<AniListAccount>>
    val activeAccountId: Flow<Int?>
    suspend fun listAccounts(): List<AniListAccount>
    /** Inserts (or updates by id) an account and makes it the active one. */
    suspend fun addOrUpdateAccount(account: AniListAccount)
    suspend fun setActiveAccount(id: Int)
    suspend fun removeAccount(id: Int)
}

/**
 * [DataStore]-backed [AccountRepository] (file `account_prefs`).
 *
 * The active account's token is mirrored into [ACTIVE_TOKEN_KEY] so [getToken]
 * (called by the OkHttp interceptor on every request) is a single fast read,
 * cached in memory. On first use, a token from the pre-multi-account
 * [TokenDataStore] (passed as [legacy]) is migrated in transparently.
 *
 * JVM unit tests use `FakeAccountRepository` (DataStore needs a Context).
 */
class AccountStore(
    private val context: Context,
    private val legacy: AuthRepository? = null
) : AccountRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val ACCOUNTS_KEY = stringPreferencesKey("accounts_json")
    private val ACTIVE_TOKEN_KEY = stringPreferencesKey("active_token")
    private val ACTIVE_ID_KEY = intPreferencesKey("active_id")

    @Volatile private var cachedToken: String? = null
    @Volatile private var cacheLoaded = false

    override val accounts: Flow<List<AniListAccount>> =
        context.accountDataStore.data.map { decodeAccounts(it[ACCOUNTS_KEY]) }

    override val activeAccountId: Flow<Int?> =
        context.accountDataStore.data.map { it[ACTIVE_ID_KEY] }

    override suspend fun getToken(): String? {
        if (cacheLoaded) return cachedToken
        var token = context.accountDataStore.data.map { it[ACTIVE_TOKEN_KEY] }.first()
        if (token == null) {
            val migrated = legacy?.getToken()
            if (migrated != null) {
                context.accountDataStore.edit { it[ACTIVE_TOKEN_KEY] = migrated }
                token = migrated
            }
        }
        cachedToken = token
        cacheLoaded = true
        return token
    }

    override suspend fun saveToken(token: String) {
        context.accountDataStore.edit { it[ACTIVE_TOKEN_KEY] = token }
        cachedToken = token
        cacheLoaded = true
    }

    override suspend fun clearToken() {
        context.accountDataStore.edit { prefs ->
            val activeId = prefs[ACTIVE_ID_KEY]
            val remaining = decodeAccounts(prefs[ACCOUNTS_KEY]).filterNot { it.id == activeId }
            if (remaining.isNotEmpty()) {
                // Log out of the active account but stay signed into the rest.
                val next = remaining.first()
                prefs[ACCOUNTS_KEY] = json.encodeToString(remaining)
                prefs[ACTIVE_ID_KEY] = next.id
                prefs[ACTIVE_TOKEN_KEY] = next.token
                cachedToken = next.token
            } else {
                prefs.remove(ACCOUNTS_KEY)
                prefs.remove(ACTIVE_ID_KEY)
                prefs.remove(ACTIVE_TOKEN_KEY)
                cachedToken = null
            }
        }
        cacheLoaded = true
    }

    override suspend fun listAccounts(): List<AniListAccount> =
        decodeAccounts(context.accountDataStore.data.map { it[ACCOUNTS_KEY] }.first())

    override suspend fun addOrUpdateAccount(account: AniListAccount) {
        context.accountDataStore.edit { prefs ->
            val updated = decodeAccounts(prefs[ACCOUNTS_KEY])
                .filterNot { it.id == account.id } + account
            prefs[ACCOUNTS_KEY] = json.encodeToString(updated)
            prefs[ACTIVE_ID_KEY] = account.id
            prefs[ACTIVE_TOKEN_KEY] = account.token
        }
        cachedToken = account.token
        cacheLoaded = true
    }

    override suspend fun setActiveAccount(id: Int) {
        context.accountDataStore.edit { prefs ->
            val acc = decodeAccounts(prefs[ACCOUNTS_KEY]).find { it.id == id } ?: return@edit
            prefs[ACTIVE_ID_KEY] = acc.id
            prefs[ACTIVE_TOKEN_KEY] = acc.token
            cachedToken = acc.token
        }
        cacheLoaded = true
    }

    override suspend fun removeAccount(id: Int) {
        context.accountDataStore.edit { prefs ->
            val remaining = decodeAccounts(prefs[ACCOUNTS_KEY]).filterNot { it.id == id }
            prefs[ACCOUNTS_KEY] = json.encodeToString(remaining)
            if (prefs[ACTIVE_ID_KEY] == id) {
                val next = remaining.firstOrNull()
                if (next != null) {
                    prefs[ACTIVE_ID_KEY] = next.id
                    prefs[ACTIVE_TOKEN_KEY] = next.token
                    cachedToken = next.token
                } else {
                    prefs.remove(ACTIVE_ID_KEY)
                    prefs.remove(ACTIVE_TOKEN_KEY)
                    cachedToken = null
                }
            }
        }
        cacheLoaded = true
    }

    private fun decodeAccounts(raw: String?): List<AniListAccount> =
        if (raw == null) emptyList()
        else runCatching { json.decodeFromString<List<AniListAccount>>(raw) }.getOrDefault(emptyList())
}
