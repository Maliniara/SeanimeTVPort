package com.malina.seanimetvport.data.anilist

import com.malina.seanimetvport.BuildConfig
import com.malina.seanimetvport.data.anilist.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException

private const val ANILIST_ENDPOINT = "https://graphql.anilist.co"

class AniListApiClient(
    private val authRepository: AuthRepository,
    private val baseUrl: String = ANILIST_ENDPOINT
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val token = runBlocking { authRepository.getToken() }
            val request = if (token != null) {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                    // The bearer token is the user's AniList account. Even in debug
                    // it has no business sitting in logcat in full.
                    redactHeader("Authorization")
                })
                // DEBUG ONLY: emulator AOSP images lack the Google Trust Services
                // root CA that now signs AniList's cert, so strict validation fails
                // with "chain validation failed". Relax TLS in debug builds so the
                // emulator works. Release builds keep full validation on the token.
                com.malina.seanimetvport.data.net.StreamingHttpClient.applyLenientTls(this)
            }
        }
        .build()

    suspend fun graphql(
        query: String,
        variables: Map<String, Any?> = emptyMap()
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBody = buildJsonObject {
                put("query", query)
                put("variables", buildJsonObject {
                    variables.forEach { (key, value) ->
                        when (value) {
                            is String -> put(key, value)
                            is Int -> put(key, value)
                            is Boolean -> put(key, value)
                            is Long -> put(key, value)
                            is Double -> put(key, value)
                            null -> Unit  // omit null values; GraphQL treats absent args as unfiltered
                            is List<*> -> put(key, buildJsonArray {
                                value.filterNotNull().forEach { item ->
                                    when (item) {
                                        is String -> add(item)
                                        is Int -> add(item)
                                        else -> add(item.toString())
                                    }
                                }
                            })
                            // Input objects (e.g. FuzzyDateInput) — one level of
                            // scalar fields, nulls omitted
                            is Map<*, *> -> put(key, buildJsonObject {
                                value.forEach { (k, v) ->
                                    val name = k.toString()
                                    when (v) {
                                        is String -> put(name, v)
                                        is Int -> put(name, v)
                                        is Boolean -> put(name, v)
                                        is Long -> put(name, v)
                                        is Double -> put(name, v)
                                        null -> Unit
                                        else -> put(name, v.toString())
                                    }
                                }
                            })
                            else -> put(key, value.toString())
                        }
                    }
                })
            }.toString()

            val body = requestBody.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(baseUrl)
                .post(body)
                .build()

            executeWithRateLimitRetry(request)
        }
    }

    /**
     * Executes the call, retrying once on HTTP 429 after honouring the
     * Retry-After header (capped at 15s). AniList's public API is limited to
     * ~30 requests/minute, so a single respectful retry resolves most bursts;
     * a second 429 surfaces a user-friendly message instead of "HTTP 429".
     */
    private suspend fun executeWithRateLimitRetry(request: Request): JsonObject {
        var attempt = 0
        while (true) {
            val response = httpClient.newCall(request).execute()
            if (response.code == 429 && attempt == 0) {
                val retryAfterSeconds = response.header("Retry-After")
                    ?.toLongOrNull()?.coerceIn(1, 15) ?: 5
                response.close()
                delay(retryAfterSeconds * 1000)
                attempt++
                continue
            }
            response.use {
                if (it.code == 429) {
                    throw IOException("AniList rate limit reached — please wait a minute and try again")
                }
                if (!it.isSuccessful) {
                    throw IOException("HTTP ${it.code}")
                }
                val responseStr = it.body?.string()
                    ?: throw IOException("Empty response body")
                val jsonResponse = json.parseToJsonElement(responseStr).jsonObject
                if (jsonResponse.containsKey("errors")) {
                    throw IOException("GraphQL errors: ${jsonResponse["errors"]}")
                }
                return jsonResponse["data"]?.jsonObject
                    ?: throw IOException("No data field in response")
            }
        }
    }
}
