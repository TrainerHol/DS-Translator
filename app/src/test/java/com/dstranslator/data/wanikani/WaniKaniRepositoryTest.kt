package com.dstranslator.data.wanikani

import com.dstranslator.data.db.WaniKaniAssignmentEntity
import com.dstranslator.data.db.WaniKaniDao
import com.dstranslator.data.settings.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WaniKaniRepositoryTest {

    private lateinit var client: OkHttpClient
    private lateinit var waniKaniDao: WaniKaniDao
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var repository: WaniKaniRepository

    @Before
    fun setup() {
        client = mockk()
        waniKaniDao = mockk(relaxed = true)
        settingsRepository = mockk()
        repository = WaniKaniRepository(client, waniKaniDao, settingsRepository)
    }

    @Test
    fun `syncAssignments noApiKey doesNothing`() = runTest {
        coEvery { settingsRepository.getWaniKaniApiKey() } returns null

        repository.syncAssignments()

        coVerify(exactly = 0) { waniKaniDao.upsertAll(any()) }
    }

    @Test
    fun `syncAssignments fetchesAndUpsertsAssignments`() = runTest {
        coEvery { settingsRepository.getWaniKaniApiKey() } returns "test-api-key"

        // Mock subjects response
        val subjectsResponse = buildSubjectsResponse(
            subjects = listOf(Pair(1, "火"), Pair(2, "水")),
            nextUrl = null
        )
        // Mock assignments response
        val assignmentsResponse = buildAssignmentsResponse(
            assignments = listOf(
                Triple(1, "kanji", 6),
                Triple(2, "kanji", 3)
            ),
            nextUrl = null
        )

        val callSubjects = mockk<Call>()
        val callAssignments = mockk<Call>()

        every { client.newCall(any()) } answers {
            val request = firstArg<Request>()
            if (request.url.toString().contains("/subjects")) {
                callSubjects
            } else {
                callAssignments
            }
        }

        every { callSubjects.execute() } returns buildOkResponse(
            subjectsResponse, "https://api.wanikani.com/v2/subjects"
        )
        every { callAssignments.execute() } returns buildOkResponse(
            assignmentsResponse, "https://api.wanikani.com/v2/assignments"
        )

        repository.syncAssignments()

        coVerify { waniKaniDao.upsertAll(any()) }
    }

    @Test
    fun `syncAssignments followsPagination`() = runTest {
        coEvery { settingsRepository.getWaniKaniApiKey() } returns "test-api-key"

        // Page 1 of subjects
        val subjectsPage1 = buildSubjectsResponse(
            subjects = listOf(Pair(1, "火")),
            nextUrl = "https://api.wanikani.com/v2/subjects?page_after_id=1"
        )
        // Page 2 of subjects
        val subjectsPage2 = buildSubjectsResponse(
            subjects = listOf(Pair(2, "水")),
            nextUrl = null
        )
        // Single page of assignments
        val assignmentsResponse = buildAssignmentsResponse(
            assignments = listOf(
                Triple(1, "kanji", 6),
                Triple(2, "kanji", 3)
            ),
            nextUrl = null
        )

        val calls = mutableListOf<Call>()
        every { client.newCall(any()) } answers {
            val call = mockk<Call>()
            val request = firstArg<Request>()
            val url = request.url.toString()
            when {
                url.contains("page_after_id") -> {
                    every { call.execute() } returns buildOkResponse(
                        subjectsPage2, url
                    )
                }
                url.contains("/subjects") -> {
                    every { call.execute() } returns buildOkResponse(
                        subjectsPage1, url
                    )
                }
                else -> {
                    every { call.execute() } returns buildOkResponse(
                        assignmentsResponse, url
                    )
                }
            }
            calls.add(call)
            call
        }

        repository.syncAssignments()

        // Should have made 3 calls: 2 subjects pages + 1 assignments page
        assertEquals(3, calls.size)
        coVerify { waniKaniDao.upsertAll(any()) }
    }

    @Test
    fun `isKanjiLearned srsAbove5 returnsTrue`() = runTest {
        coEvery { waniKaniDao.getAssignmentForKanji("火") } returns WaniKaniAssignmentEntity(
            subjectId = 1,
            subjectType = "kanji",
            srsStage = 6,
            passedAt = "2024-01-01T00:00:00Z",
            kanjiCharacter = "火"
        )

        assertTrue(repository.isKanjiLearned("火"))
    }

    @Test
    fun `isKanjiLearned srsEqual5 returnsTrue`() = runTest {
        coEvery { waniKaniDao.getAssignmentForKanji("水") } returns WaniKaniAssignmentEntity(
            subjectId = 2,
            subjectType = "kanji",
            srsStage = 5,
            passedAt = null,
            kanjiCharacter = "水"
        )

        assertTrue(repository.isKanjiLearned("水"))
    }

    @Test
    fun `isKanjiLearned srsBelow5 returnsFalse`() = runTest {
        coEvery { waniKaniDao.getAssignmentForKanji("木") } returns WaniKaniAssignmentEntity(
            subjectId = 3,
            subjectType = "kanji",
            srsStage = 3,
            passedAt = null,
            kanjiCharacter = "木"
        )

        assertFalse(repository.isKanjiLearned("木"))
    }

    @Test
    fun `isKanjiLearned unknownKanji returnsFalse`() = runTest {
        coEvery { waniKaniDao.getAssignmentForKanji("龍") } returns null

        assertFalse(repository.isKanjiLearned("龍"))
    }

    @Test
    fun `getLearnedKanji returnsSetFromDao`() = runTest {
        coEvery { waniKaniDao.getLearnedKanji() } returns listOf("火", "水", "木")

        val result = repository.getLearnedKanji()

        assertEquals(setOf("火", "水", "木"), result)
    }

    @Test
    fun `clearData callsDaoClearAll`() = runTest {
        repository.clearData()

        coVerify { waniKaniDao.clearAll() }
    }

    // --- Helper methods ---

    private fun buildSubjectsResponse(
        subjects: List<Pair<Int, String>>,
        nextUrl: String?
    ): String {
        return JSONObject().apply {
            put("data", JSONArray().apply {
                subjects.forEach { (id, char) ->
                    put(JSONObject().apply {
                        put("id", id)
                        put("object", "kanji")
                        put("data", JSONObject().apply {
                            put("characters", char)
                        })
                    })
                }
            })
            put("pages", JSONObject().apply {
                put("next_url", nextUrl ?: JSONObject.NULL)
            })
        }.toString()
    }

    private fun buildAssignmentsResponse(
        assignments: List<Triple<Int, String, Int>>,
        nextUrl: String?
    ): String {
        return JSONObject().apply {
            put("data", JSONArray().apply {
                assignments.forEach { (subjectId, subjectType, srsStage) ->
                    put(JSONObject().apply {
                        put("data", JSONObject().apply {
                            put("subject_id", subjectId)
                            put("subject_type", subjectType)
                            put("srs_stage", srsStage)
                            put("passed_at", JSONObject.NULL)
                        })
                    })
                }
            })
            put("pages", JSONObject().apply {
                put("next_url", nextUrl ?: JSONObject.NULL)
            })
        }.toString()
    }

    private fun buildOkResponse(body: String, url: String): Response {
        return Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }
}
