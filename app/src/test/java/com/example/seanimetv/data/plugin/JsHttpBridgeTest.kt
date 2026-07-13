package com.example.seanimetv.data.plugin

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class JsHttpBridgeTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var bridge: JsHttpBridge

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        bridge = JsHttpBridge(OkHttpClient())
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    /**
     * fetch_bodyIsFullyBuffered — the response body string is accessible in the returned JsResponse.
     * Verifies the critical constraint: body must be fully in memory before JsResponse is returned,
     * because the plugin calls .text() and .json() synchronously with no await.
     */
    @Test
    fun fetch_bodyIsFullyBuffered() {
        val expectedBody = "<html><body>Hello, plugin world!</body></html>"
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(expectedBody)
        )

        val response = bridge.fetch(mockWebServer.url("/").toString(), null)

        assertEquals(expectedBody, response.body)
        assertTrue(response.ok)
        assertEquals(200, response.status)
    }

    /**
     * fetch_sendsCustomHeaders — headers decoded from the JSON string are forwarded in the request.
     */
    @Test
    fun fetch_sendsCustomHeaders() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        bridge.fetch(
            mockWebServer.url("/").toString(),
            """{"X-Custom-Header":"test-value","Referer":"https://example.com"}"""
        )

        val recorded = mockWebServer.takeRequest(1, TimeUnit.SECONDS)
        assertEquals("test-value", recorded?.getHeader("X-Custom-Header"))
        assertEquals("https://example.com", recorded?.getHeader("Referer"))
    }

    /**
     * fetch_returnsOkFalse_on404 — a 404 response sets ok=false and preserves the status code.
     */
    @Test
    fun fetch_returnsOkFalse_on404() {
        mockWebServer.enqueue(MockResponse().setResponseCode(404).setBody("not found"))

        val response = bridge.fetch(mockWebServer.url("/").toString(), null)

        assertFalse(response.ok)
        assertEquals(404, response.status)
    }

    /**
     * fetch_doesNotThrow_onNetworkFailure — a connection error returns a safe failure JsResponse
     * instead of propagating an exception (bridge failures must not crash QuickJS).
     */
    @Test
    fun fetch_doesNotThrow_onNetworkFailure() {
        // Port 1 is blocked on all OSes — guarantees a connection refused error
        val response = bridge.fetch("http://127.0.0.1:1", null)

        assertFalse(response.ok)
        assertEquals(0, response.status)
        assertEquals("", response.body)
    }

    /**
     * fetch_noHeaders_sendsRequest — passing null headers still produces a valid request.
     */
    @Test
    fun fetch_noHeaders_sendsRequest() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("data"))

        val response = bridge.fetch(mockWebServer.url("/").toString(), null)

        assertTrue(response.ok)
        assertEquals("data", response.body)
    }
}
