package com.dstranslator.data.translation

import com.dstranslator.data.settings.SettingsRepository
import io.mockk.coEvery
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class ClaudeTranslationEngineTest {

    private lateinit var client: OkHttpClient
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var engine: ClaudeTranslationEngine

    @Before
    fun setUp() {
        client = mockk()
        settingsRepository = mockk()
        engine = ClaudeTranslationEngine(client, settingsRepository)
    }

    @Test
    fun `name is Claude`() {
        assertEquals("Claude", engine.name)
    }

    @Test
    fun `requiresApiKey is true`() {
        assertTrue(engine.requiresApiKey)
    }

    @Test
    fun `translate builds correct request and parses response`() = runTest {
        // Arrange
        coEvery { settingsRepository.getClaudeApiKey() } returns "sk-ant-test-key"

        val responseJson = JSONObject().apply {
            put("content", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", "Hello world")
                })
            })
        }

        val call = mockk<Call>()
        every { client.newCall(any()) } answers {
            val request = firstArg<Request>()
            // Verify request URL
            assertEquals("https://api.anthropic.com/v1/messages", request.url.toString())
            // Verify headers: x-api-key (NOT Bearer auth)
            assertEquals("sk-ant-test-key", request.header("x-api-key"))
            assertEquals("2023-06-01", request.header("anthropic-version"))
            assertEquals("application/json", request.header("Content-Type"))
            // Verify NO Authorization header
            assertEquals(null, request.header("Authorization"))
            call
        }
        every { call.execute() } returns Response.Builder()
            .request(Request.Builder().url("https://api.anthropic.com/v1/messages").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseJson.toString().toResponseBody("application/json".toMediaType()))
            .build()

        // Act
        val result = engine.translate("konnichiwa")

        // Assert
        assertEquals("Hello world", result)
    }

    @Test
    fun `translate sends correct request body structure`() = runTest {
        // Arrange
        coEvery { settingsRepository.getClaudeApiKey() } returns "sk-ant-test-key"

        val responseJson = JSONObject().apply {
            put("content", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", "Translation")
                })
            })
        }

        var capturedRequestBody: String? = null
        val call = mockk<Call>()
        every { client.newCall(any()) } answers {
            val request = firstArg<Request>()
            val buffer = okio.Buffer()
            request.body!!.writeTo(buffer)
            capturedRequestBody = buffer.readUtf8()
            call
        }
        every { call.execute() } returns Response.Builder()
            .request(Request.Builder().url("https://api.anthropic.com/v1/messages").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseJson.toString().toResponseBody("application/json".toMediaType()))
            .build()

        // Act
        engine.translate("test text")

        // Assert
        val bodyJson = JSONObject(capturedRequestBody!!)
        assertEquals("claude-sonnet-4-20250514", bodyJson.getString("model"))
        assertEquals(1024, bodyJson.getInt("max_tokens"))
        assertTrue(bodyJson.has("system"))
        val messages = bodyJson.getJSONArray("messages")
        assertEquals(1, messages.length())
        assertEquals("user", messages.getJSONObject(0).getString("role"))
        assertEquals("test text", messages.getJSONObject(0).getString("content"))
    }

    @Test(expected = IllegalStateException::class)
    fun `translate throws IllegalStateException when API key is null`() = runTest {
        // Arrange
        coEvery { settingsRepository.getClaudeApiKey() } returns null

        // Act
        engine.translate("test")
    }

    @Test
    fun `translate throws IOException on non-2xx response`() = runTest {
        // Arrange
        coEvery { settingsRepository.getClaudeApiKey() } returns "sk-ant-test-key"

        val call = mockk<Call>()
        every { client.newCall(any()) } returns call
        every { call.execute() } returns Response.Builder()
            .request(Request.Builder().url("https://api.anthropic.com/v1/messages").build())
            .protocol(Protocol.HTTP_1_1)
            .code(500)
            .message("Internal Server Error")
            .body("error".toResponseBody("text/plain".toMediaType()))
            .build()

        // Act & Assert
        try {
            engine.translate("test")
            assertTrue("Should have thrown IOException", false)
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("500"))
        }
    }
}
