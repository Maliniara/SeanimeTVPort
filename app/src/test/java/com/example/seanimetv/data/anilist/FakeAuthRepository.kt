package com.example.seanimetv.data.anilist

import com.example.seanimetv.data.anilist.repository.AuthRepository

class FakeAuthRepository(initial: String? = null) : AuthRepository {
    private var token: String? = initial

    override suspend fun getToken(): String? = token
    override suspend fun saveToken(token: String) { this.token = token }
    override suspend fun clearToken() { token = null }
}
