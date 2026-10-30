package com.malina.seanimetvport.data.anilist

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TokenDataStoreTest {

    /**
     * saveToken_persists — after saveToken, getToken returns the saved value
     */
    @Test
    fun saveToken_persists() = runTest {
        val repo = FakeAuthRepository()

        repo.saveToken("my_token")

        assertEquals("my_token", repo.getToken())
    }

    /**
     * getToken_returnsNull_whenEmpty — fresh repository with no token returns null
     */
    @Test
    fun getToken_returnsNull_whenEmpty() = runTest {
        val repo = FakeAuthRepository()

        assertNull(repo.getToken())
    }

    /**
     * clearToken_removesValue — after clearToken, getToken returns null
     */
    @Test
    fun clearToken_removesValue() = runTest {
        val repo = FakeAuthRepository(initial = "existing_token")

        repo.clearToken()

        assertNull(repo.getToken())
    }
}
