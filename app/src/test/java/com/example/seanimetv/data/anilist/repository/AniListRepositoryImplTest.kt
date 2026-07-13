package com.example.seanimetv.data.anilist.repository

import com.example.seanimetv.data.anilist.AniListApiClient
import com.example.seanimetv.data.anilist.FakeAuthRepository
import com.example.seanimetv.data.anilist.model.MediaListStatus
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AniListRepositoryImplTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiClient: AniListApiClient
    private lateinit var repository: AniListRepositoryImpl

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        apiClient = AniListApiClient(
            authRepository = FakeAuthRepository("test_token"),
            baseUrl = mockWebServer.url("/").toString()
        )
        repository = AniListRepositoryImpl(apiClient)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    /**
     * getViewer_success — a valid Viewer response decodes to AniListUser with the correct id and name
     */
    @Test
    fun getViewer_success() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"data":{"Viewer":{"id":42,"name":"testuser"}}}""")
        )

        val result = repository.getViewer()

        assertTrue(result.isSuccess)
        val user = result.getOrThrow()
        assertEquals(42, user.id)
        assertEquals("testuser", user.name)
    }

    /**
     * getMediaList_mapsToAnimeEntry — a valid MediaListCollection response decodes to a list of
     * AniListMediaListEntry with matching id, progress, status, and nested media fields
     */
    @Test
    fun getMediaList_mapsToAnimeEntry() = runTest {
        val body = """
            {
              "data": {
                "MediaListCollection": {
                  "lists": [{
                    "entries": [{
                      "id": 1,
                      "progress": 5,
                      "status": "CURRENT",
                      "media": {
                        "id": 101,
                        "title": {"romaji": "Naruto", "english": "Naruto"},
                        "coverImage": {"large": "http://img/naruto.jpg"}
                      }
                    }]
                  }]
                }
              }
            }
        """.trimIndent()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(body)
        )

        val result = repository.getMediaList(userId = 42, status = MediaListStatus.CURRENT)

        assertTrue(result.isSuccess)
        val entries = result.getOrThrow()
        assertEquals(1, entries.size)
        val entry = entries[0]
        assertEquals(1, entry.id)
        assertEquals(5, entry.progress)
        assertEquals("CURRENT", entry.status)
        assertEquals(101, entry.media.id)
        assertEquals("Naruto", entry.media.title?.english)
        assertEquals("http://img/naruto.jpg", entry.media.coverImage?.large)
    }

    /**
     * saveProgress_callsMutation — on a successful SaveMediaListEntry response,
     * saveProgress returns Result.success(Unit)
     */
    @Test
    fun saveProgress_callsMutation() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"data":{"SaveMediaListEntry":{"id":1}}}""")
        )

        val result = repository.saveProgress(
            mediaId = 101,
            progress = 5,
            status = MediaListStatus.CURRENT
        )

        assertTrue(result.isSuccess)
    }
}
