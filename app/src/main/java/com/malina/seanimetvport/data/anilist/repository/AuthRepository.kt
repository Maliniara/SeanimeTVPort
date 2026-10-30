package com.malina.seanimetvport.data.anilist.repository

interface AuthRepository {
    suspend fun getToken(): String?
    suspend fun saveToken(token: String)
    suspend fun clearToken()
}
