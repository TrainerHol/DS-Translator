package com.dstranslator.data.wanikani

import com.dstranslator.data.db.WaniKaniAssignmentEntity
import com.dstranslator.data.db.WaniKaniDao
import com.dstranslator.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages WaniKani assignment data: syncs from the WaniKani API v2,
 * stores in local Room DB, and provides lookup methods for kanji learning status.
 *
 * Two-phase sync:
 * 1. Fetch /v2/subjects?types=kanji to build subjectId -> kanjiCharacter map
 * 2. Fetch /v2/assignments?subject_types=kanji to get SRS stages
 * 3. Merge and upsert into local database
 *
 * SRS stage >= 5 (Guru+) means a kanji is "learned".
 */
@Singleton
class WaniKaniRepository @Inject constructor(
    private val client: OkHttpClient,
    private val waniKaniDao: WaniKaniDao,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val BASE_URL = "https://api.wanikani.com/v2"
        private const val RATE_LIMIT_DELAY_MS = 1000L
    }

    /**
     * Full sync of WaniKani kanji assignments with two-phase pagination:
     * 1. Fetch all kanji subjects to get subjectId -> character mapping
     * 2. Fetch all kanji assignments to get SRS stages
     * 3. Merge and upsert into local database
     *
     * Does nothing if WaniKani API key is not configured.
     */
    suspend fun syncAssignments() = withContext(Dispatchers.IO) {
        val apiKey = settingsRepository.getWaniKaniApiKey() ?: return@withContext

        // Phase 1: Fetch subjects to get kanji characters
        val subjectMap = mutableMapOf<Int, String>() // subjectId -> kanjiCharacter
        var nextUrl: String? = "$BASE_URL/subjects?types=kanji"

        while (nextUrl != null) {
            val request = Request.Builder()
                .url(nextUrl)
                .header("Authorization", "Bearer $apiKey")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) break

            val json = JSONObject(response.body!!.string())
            val data = json.getJSONArray("data")

            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val id = item.getInt("id")
                val characters: String? = item.getJSONObject("data").optString("characters", null)
                if (characters != null) {
                    subjectMap[id] = characters
                }
            }

            val rawNext: String? = json.getJSONObject("pages")
                .optString("next_url", null)
            nextUrl = rawNext?.takeIf { it.isNotEmpty() && it != "null" }

            if (nextUrl != null) {
                delay(RATE_LIMIT_DELAY_MS)
            }
        }

        // Phase 2: Fetch assignments and merge with subject characters
        nextUrl = "$BASE_URL/assignments?subject_types=kanji"

        while (nextUrl != null) {
            val request = Request.Builder()
                .url(nextUrl)
                .header("Authorization", "Bearer $apiKey")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) break

            val json = JSONObject(response.body!!.string())
            val data = json.getJSONArray("data")

            val assignments = (0 until data.length()).map { i ->
                val itemData = data.getJSONObject(i).getJSONObject("data")
                val subjectId = itemData.getInt("subject_id")
                WaniKaniAssignmentEntity(
                    subjectId = subjectId,
                    subjectType = itemData.getString("subject_type"),
                    srsStage = itemData.getInt("srs_stage"),
                    passedAt = (itemData.optString("passed_at", null) as String?)
                        ?.takeIf { it.isNotEmpty() && it != "null" },
                    kanjiCharacter = subjectMap[subjectId]
                )
            }

            if (assignments.isNotEmpty()) {
                waniKaniDao.upsertAll(assignments)
            }

            val rawNext: String? = json.getJSONObject("pages")
                .optString("next_url", null)
            nextUrl = rawNext?.takeIf { it.isNotEmpty() && it != "null" }

            if (nextUrl != null) {
                delay(RATE_LIMIT_DELAY_MS)
            }
        }
    }

    /**
     * Check if a kanji character has been learned (SRS stage >= 5, Guru+).
     *
     * @param kanji Single kanji character to check
     * @return true if the kanji is at Guru level or above, false otherwise
     */
    suspend fun isKanjiLearned(kanji: String): Boolean {
        val assignment = waniKaniDao.getAssignmentForKanji(kanji)
        return assignment?.srsStage?.let { it >= 5 } ?: false
    }

    /**
     * Get all kanji characters that have been learned (SRS stage >= 5).
     *
     * @return Set of learned kanji characters
     */
    suspend fun getLearnedKanji(): Set<String> {
        return waniKaniDao.getLearnedKanji().toSet()
    }

    /**
     * Clear all cached WaniKani assignment data.
     */
    suspend fun clearData() {
        waniKaniDao.clearAll()
    }
}
