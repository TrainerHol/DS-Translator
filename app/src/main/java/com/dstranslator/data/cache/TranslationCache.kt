package com.dstranslator.data.cache

import androidx.collection.LruCache
import com.dstranslator.data.db.CachedTranslationDao
import com.dstranslator.data.db.CachedTranslationEntity

/**
 * Two-tier translation cache: in-memory LRU fronting Room persistent database.
 *
 * Lookup order: LRU in-memory -> Room database -> null (cache miss).
 * On Room hit, the entry is promoted to the LRU for subsequent fast access.
 * Writes go to both tiers simultaneously.
 *
 * Uses androidx.collection.LruCache (pure JVM) instead of android.util.LruCache
 * for unit test compatibility.
 */
class TranslationCache(
    private val cachedTranslationDao: CachedTranslationDao,
    maxMemoryEntries: Int = 500
) {
    private val memoryCache = LruCache<String, String>(maxMemoryEntries)

    /**
     * Look up a translation by Japanese source text.
     * Returns English translation or null on cache miss.
     */
    suspend fun get(japaneseText: String): String? {
        // Tier 1: In-memory LRU
        memoryCache.get(japaneseText)?.let { return it }

        // Tier 2: Room database
        val dbEntry = cachedTranslationDao.findBySourceText(japaneseText)
        if (dbEntry != null) {
            // Promote to memory cache
            memoryCache.put(japaneseText, dbEntry.translatedText)
            return dbEntry.translatedText
        }

        return null // Cache miss
    }

    /**
     * Store a translation in both LRU and Room.
     */
    suspend fun put(japaneseText: String, englishText: String) {
        memoryCache.put(japaneseText, englishText)
        cachedTranslationDao.insert(
            CachedTranslationEntity(
                sourceText = japaneseText,
                translatedText = englishText,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    /**
     * Clear both in-memory LRU and Room cache table.
     */
    suspend fun clear() {
        memoryCache.evictAll()
        cachedTranslationDao.deleteAll()
    }
}
