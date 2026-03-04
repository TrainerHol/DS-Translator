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

class OpenAiTranslationEngineTest {

    private lateinit var client: OkHttpClient
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var engine: OpenAiTranslationEngine

    @Before
    fun setUp() {
        client = mockk()
        settingsRepository = mockk()
        engine = OpenAiTranslationEngine(client, settingsRepository)
    }

    @Test
    fun `name is OpenAI`() {
        assertEquals("OpenAI", engine.name)
    }

    @Test
    fun `requiresApiKey is true`() {
        assertTrue(engine.requiresApiKey)
    }

    @Test
    fun `translate builds correct request and parses response`() = runTest {
        // Arrange
        coEvery { settingsRepository.getOpenAiApiKey() } returns "sk-test-key"
        coEvery { settingsRepository.getOpenAiBaseUrl() } returns null
        coEvery { settingsRepository.getOpenAiModel() } returns null

        val responseJson = JSONObject().apply {
            put("choices", JSONArray().apply {
                put(JSONObject().apply {
                    put("message", JSONObject().apply {
                        put("content", "Hello world")
                    })
                })
            })
        }

        val call = mockk<Call>()
        every { client.newCall(any()) } answers {
            val request = firstArg<Request>()
            // Verify request URL uses default base URL
            assertEquals("https://api.openai.com/v1/chat/completions", request.url.toString())
            // Verify auth header
            assertEquals("Bearer sk-test-key", request.header("Authorization"))
            assertEquals("application/json", request.header("Content-Type"))
            call
        }
        every { call.execute() } returns Response.Builder()
            .request(Request.Builder().url("https://api.openai.com/v1/chat/completions").build())
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
    fun `translate strips trailing slash from custom base URL`() = runTest {
        // Arrange
        coEvery { settingsRepository.getOpenAiApiKey() } returns "sk-test-key"
        coEvery { settingsRepository.getOpenAiBaseUrl() } returns "http://localhost:11434/v1/"
        coEvery { settingsRepository.getOpenAiModel() } returns "llama3"

        val responseJson = JSONObject().apply {
            put("choices", JSONArray().apply {
                put(JSONObject().apply {
                    put("message", JSONObject().apply {
                        put("content", "Local translation")
                    })
                })
            })
        }

        val call = mockk<Call>()
        every { client.newCall(any()) } answers {
            val request = firstArg<Request>()
            // Verify trailing slash is stripped -- no double slash
            assertEquals("http://localhost:11434/v1/chat/completions", request.url.toString())
            call
        }
        every { call.execute() } returns Response.Builder()
            .request(Request.Builder().url("http://localhost:11434/v1/chat/completions").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseJson.toString().toResponseBody("application/json".toMediaType()))
            .build()

        // Act
        val result = engine.translate("test")

        // Assert
        assertEquals("Local translation", result)
    }

    @Test(expected = IllegalStateException::class)
    fun `translate throws IllegalStateException when API key is null`() = runTest {
        // Arrange
        coEvery { settingsRepository.getOpenAiApiKey() } returns null

        // Act
        engine.translate("test")
    }

    @Test
    fun `translate throws IOException on non-2xx response`() = runTest {
        // Arrange
        coEvery { settingsRepository.getOpenAiApiKey() } returns "sk-test-key"
        coEvery { settingsRepository.getOpenAiBaseUrl() } returns null
        coEvery { settingsRepository.getOpenAiModel() } returns null

        val call = mockk<Call>()
        every { client.newCall(any()) } returns call
        every { call.execute() } returns Response.Builder()
            .request(Request.Builder().url("https://api.openai.com/v1/chat/completions").build())
            .protocol(Protocol.HTTP_1_1)
            .code(429)
            .message("Too Many Requests")
            .body("rate limited".toResponseBody("text/plain".toMediaType()))
            .build()

        // Act & Assert
        try {
            engine.translate("test")
            assertTrue("Should have thrown IOException", false)
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("429"))
        }
    }

    @Test
    fun `translate uses custom model from settings`() = runTest {
        // Arrange
        coEvery { settingsRepository.getOpenAiApiKey() } returns "sk-test-key"
        coEvery { settingsRepository.getOpenAiBaseUrl() } returns null
        coEvery { settingsRepository.getOpenAiModel() } returns "gpt-4o"

        val responseJson = JSONObject().apply {
            put("choices", JSONArray().apply {
                put(JSONObject().apply {
                    put("message", JSONObject().apply {
                        put("content", "Translated text")
                    })
                })
            })
        }

        var capturedRequestBody: String? = null
        val call = mockk<Call>()
        every { client.newCall(any()) } answers {
            val request = firstArg<Request>()
            // Capture request body to verify model
            val buffer = okio.Buffer()
            request.body!!.writeTo(buffer)
            capturedRequestBody = buffer.readUtf8()
            call
        }
        every { call.execute() } returns Response.Builder()
            .request(Request.Builder().url("https://api.openai.com/v1/chat/completions").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseJson.toString().toResponseBody("application/json".toMediaType()))
            .build()

        // Act
        engine.translate("test")

        // Assert
        val bodyJson = JSONObject(capturedRequestBody!!)
        assertEquals("gpt-4o", bodyJson.getString("model"))
    }
}
