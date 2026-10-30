package com.malina.seanimetvport.data.anilist

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [AccountRepository] for unit tests, mirroring [AccountStore]'s
 * active-account semantics without a DataStore/Context.
 */
class FakeAccountRepository(
    initialAccounts: List<AniListAccount> = emptyList(),
    initialActiveId: Int? = initialAccounts.firstOrNull()?.id
) : AccountRepository {

    private val state = MutableStateFlow(initialAccounts)
    private val activeId = MutableStateFlow(initialActiveId)
    private var token: String? = initialAccounts.find { it.id == initialActiveId }?.token

    override val accounts: Flow<List<AniListAccount>> = state
    override val activeAccountId: Flow<Int?> = activeId

    override suspend fun getToken(): String? = token
    override suspend fun saveToken(token: String) { this.token = token }

    override suspend fun clearToken() {
        val remaining = state.value.filterNot { it.id == activeId.value }
        state.value = remaining
        val next = remaining.firstOrNull()
        activeId.value = next?.id
        token = next?.token
    }

    override suspend fun listAccounts(): List<AniListAccount> = state.value

    override suspend fun addOrUpdateAccount(account: AniListAccount) {
        state.value = state.value.filterNot { it.id == account.id } + account
        activeId.value = account.id
        token = account.token
    }

    override suspend fun setActiveAccount(id: Int) {
        val acc = state.value.find { it.id == id } ?: return
        activeId.value = acc.id
        token = acc.token
    }

    override suspend fun removeAccount(id: Int) {
        val remaining = state.value.filterNot { it.id == id }
        state.value = remaining
        if (activeId.value == id) {
            val next = remaining.firstOrNull()
            activeId.value = next?.id
            token = next?.token
        }
    }
}
