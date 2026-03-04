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
 * OpenAI-compatible translation engine using the Chat Completions API.
 * Works with OpenAI, Ollama, LM Studio, text-generation-webui, or any OpenAI-compatible proxy
 * via configurable base URL.
 */
@Singleton
class OpenAiTranslationEngine @Inject constructor(
    private val client: OkHttpClient,
    private val settingsRepository: SettingsRepository
) : TranslationEngine {

    override val name: String = "OpenAI"
    override val requiresApiKey: Boolean = true

    override suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = settingsRepository.getOpenAiApiKey()
            ?: throw IllegalStateException("OpenAI API key not configured")

        val baseUrl = (settingsRepository.getOpenAiBaseUrl()
            ?.trimEnd('/'))
            ?: DEFAULT_BASE_URL

        val model = settingsRepository.getOpenAiModel() ?: DEFAULT_MODEL

        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", text)
                })
            })
            put("max_tokens", 1024)
            put("temperature", 0.3)
        }

        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("OpenAI API error: ${response.code} ${response.message}")
        }

        val responseJson = JSONObject(response.body!!.string())
        responseJson.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
    }

    companion object {
        private const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        private const val DEFAULT_MODEL = "gpt-4o-mini"
        private const val SYSTEM_PROMPT =
            "You are a translator. Translate the following Japanese text to English. Return ONLY the translation, no explanations."
    }
}
