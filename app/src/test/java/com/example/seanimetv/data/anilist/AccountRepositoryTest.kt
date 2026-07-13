package com.example.seanimetv.data.anilist

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Contract for the multi-account [AccountRepository], exercised via
 * [FakeAccountRepository] (the real [AccountStore] needs an Android Context).
 */
class AccountRepositoryTest {

    private fun account(id: Int) =
        AniListAccount(id = id, name = "user$id", token = "token$id")

    /** Adding an account makes it active and its token the one returned. */
    @Test
    fun addOrUpdateAccount_makesItActive() = runTest {
        val repo = FakeAccountRepository()

        repo.addOrUpdateAccount(account(1))

        assertEquals("token1", repo.getToken())
        assertEquals(1, repo.activeAccountId.first())
        assertEquals(1, repo.listAccounts().size)
    }

    /** setActiveAccount switches which account's token getToken returns. */
    @Test
    fun setActiveAccount_switchesToken() = runTest {
        val repo = FakeAccountRepository()
        repo.addOrUpdateAccount(account(1))
        repo.addOrUpdateAccount(account(2)) // 2 is now active

        repo.setActiveAccount(1)

        assertEquals("token1", repo.getToken())
        assertEquals(1, repo.activeAccountId.first())
    }

    /** Removing the active account falls back to a remaining one. */
    @Test
    fun removeActiveAccount_fallsBackToAnother() = runTest {
        val repo = FakeAccountRepository()
        repo.addOrUpdateAccount(account(1))
        repo.addOrUpdateAccount(account(2)) // active

        repo.removeAccount(2)

        assertEquals("token1", repo.getToken())
        assertEquals(1, repo.activeAccountId.first())
        assertEquals(1, repo.listAccounts().size)
    }

    /** clearToken logs out the active account but keeps the others signed in. */
    @Test
    fun clearToken_withMultiple_keepsOthers() = runTest {
        val repo = FakeAccountRepository()
        repo.addOrUpdateAccount(account(1))
        repo.addOrUpdateAccount(account(2)) // active

        repo.clearToken()
        assertEquals("token1", repo.getToken())
        assertEquals(1, repo.listAccounts().size)

        repo.clearToken()
        assertNull(repo.getToken())
        assertEquals(0, repo.listAccounts().size)
    }
}
