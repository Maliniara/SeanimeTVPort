package com.example.seanimetv.data.plugin

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * HTTP bridge for the QuickJS plugin engine.
 *
 * Invoked synchronously from QuickJS via a global fetch() binding registered in
 * Group 6 (QuickJSEngine). OkHttp's [OkHttpClient.newCall] execute() call blocks
 * the calling thread, and [okhttp3.ResponseBody.string] fully buffers the entire
 * response body into a Kotlin String before the response is closed. This is the
 * critical requirement: the plugin calls `.text()` and `.json()` synchronously on
 * the returned [JsResponse] with no await, so the body must already be in memory.
 *
 * Bridge failures (network errors, invalid URLs) are caught and returned as a
 * failure [JsResponse] — they must never propagate as exceptions into QuickJS.
 */
class JsHttpBridge(private val okHttpClient: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Performs a blocking HTTP GET (or GET with headers) and returns a fully-buffered response.
     *
     * @param url The target URL.
     * @param headersJson Optional JSON object string mapping header name → value,
     *                    e.g. `{"Referer":"https://example.com"}`. Pass null for no custom headers.
     * @return A [JsResponse] with the fully-buffered body, or a failure response on error.
     */
    fun fetch(
        url: String,
        headersJson: String?,
        method: String = "GET",
        body: String? = null
    ): JsResponse {
        return try {
            val requestBuilder = Request.Builder().url(url)
            if (headersJson != null) {
                @Suppress("UNCHECKED_CAST")
                val headers: Map<String, String> = json.decodeFromString(headersJson)
                headers.forEach { (name, value) -> requestBuilder.header(name, value) }
            }
            val normalizedMethod = method.uppercase().ifBlank { "GET" }
            if (normalizedMethod != "GET") {
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                requestBuilder.method(normalizedMethod, (body ?: "").toRequestBody(mediaType))
            }
            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            // CRITICAL: buffer the entire body into memory BEFORE closing the response.
            // The plugin calls .body on the returned JsResponse synchronously.
            val body = response.body?.string() ?: ""
            response.close()
            JsResponse(ok = response.isSuccessful, status = response.code, body = body)
        } catch (e: Exception) {
            android.util.Log.w("JsHttpBridge", "$method $url failed: ${e.javaClass.simpleName}: ${e.message}")
            // Bridge failures must not crash QuickJS; return a safe failure sentinel.
            JsResponse(ok = false, status = 0, body = "")
        }
    }
}

/**
 * Kotlin-side representation of an HTTP response returned to QuickJS.
 *
 * The [body] field holds the complete, buffered response body as a String.
 * Group 6 (QuickJSEngine) exposes this as a JS object with `.ok`, `.status`,
 * `.text()`, and `.json()` bindings — the body is already in memory so those
 * calls can execute synchronously without any I/O.
 */
data class JsResponse(
    val ok: Boolean,
    val status: Int,
    val body: String
)
