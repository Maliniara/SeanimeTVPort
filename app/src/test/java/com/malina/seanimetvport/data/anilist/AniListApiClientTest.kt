package com.malina.seanimetvport.data.anilist

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class AniListApiClientTest {

    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    /**
     * graphqlRequest_hasBearerToken — every outgoing request carries Authorization: Bearer <token>
     */
    @Test
    fun graphqlRequest_hasBearerToken() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"data":{"Viewer":{"id":1,"name":"testuser"}}}""")
        )
        val client = AniListApiClient(
            authRepository = FakeAuthRepository("test_token"),
            baseUrl = mockWebServer.url("/").toString()
        )

        client.graphql(AniListQueries.VIEWER_QUERY)

        val recorded = mockWebServer.takeRequest(1, TimeUnit.SECONDS)
        assertEquals("Bearer test_token", recorded?.getHeader("Authorization"))
    }

    /**
     * graphqlRequest_401 — a 401 HTTP response produces Result.failure
     */
    @Test
    fun graphqlRequest_401() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        val client = AniListApiClient(
            authRepository = FakeAuthRepository("valid_token"),
            baseUrl = mockWebServer.url("/").toString()
        )

        val result = client.graphql(AniListQueries.VIEWER_QUERY)

        assertTrue(result.isFailure)
    }
}
