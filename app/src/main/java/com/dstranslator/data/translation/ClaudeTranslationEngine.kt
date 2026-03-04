package com.dstranslator.data.translation

import com.dstranslator.data.settings.SettingsRepository
import com.dstranslator.domain.engine.TranslationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Anthropic Claude translation engine using the Messages API.
 * Uses x-api-key header authentication (NOT Bearer token).
 */
@Singleton
class ClaudeTranslationEngine @Inject constructor(
    private val client: OkHttpClient,
    private val settingsRepository: SettingsRepository
) : TranslationEngine {

    override val name: String = "Claude"
    override val requiresApiKey: Boolean = true

    override suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = settingsRepository.getClaudeApiKey()
            ?: throw IllegalStateException("Claude API key not configured")

        val body = JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", 1024)
            put("system", SYSTEM_PROMPT)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", text)
                })
            })
        }

        val request = Request.Builder()
            .url(API_URL)
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Claude API error: ${response.code} ${response.message}")
        }

        val responseJson = JSONObject(response.body!!.string())
        responseJson.getJSONArray("content")
            .getJSONObject(0)
            .getString("text")
            .trim()
    }

    companion object {
        private const val API_URL = "https://api.anthropic.com/v1/messages"
        private const val MODEL = "claude-sonnet-4-20250514"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val SYSTEM_PROMPT =
            "You are a translator. Translate the following Japanese text to English. Return ONLY the translation, no explanations."
    }
}
