package com.dstranslator.data.cache

import com.dstranslator.data.db.CachedTranslationDao
import com.dstranslator.data.db.CachedTranslationEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TranslationCacheTest {

    private lateinit var dao: CachedTranslationDao
    private lateinit var cache: TranslationCache

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
        cache = TranslationCache(dao, maxMemoryEntries = 100)
    }

    @Test
    fun `get returns null on first lookup when both tiers miss`() = runTest {
        // DAO returns null (Room miss)
        coEvery { dao.findBySourceText(any()) } returns null

        val result = cache.get("hello")

        assertNull(result)
    }

    @Test
    fun `get returns cached value after put (LRU hit)`() = runTest {
        // After put, get should return from LRU without hitting DAO
        coEvery { dao.findBySourceText(any()) } returns null

        cache.put("hello", "world")
        val result = cache.get("hello")

        assertEquals("world", result)

        // Verify DAO findBySourceText was NOT called for the get (LRU hit)
        coVerify(exactly = 0) { dao.findBySourceText("hello") }
    }

    @Test
    fun `clear causes subsequent get to return null`() = runTest {
        coEvery { dao.findBySourceText(any()) } returns null

        cache.put("hello", "world")
        cache.clear()
        val result = cache.get("hello")

        assertNull(result)
        // Verify DAO deleteAll was called
        coVerify { dao.deleteAll() }
    }

    @Test
    fun `get promotes from Room to LRU on Room hit`() = runTest {
        // First call: DAO returns a cached entry (Room hit)
        coEvery { dao.findBySourceText("hello") } returns CachedTranslationEntity(
            sourceText = "hello",
            translatedText = "world",
            timestamp = System.currentTimeMillis()
        )

        // First get -- should hit Room and promote to LRU
        val result1 = cache.get("hello")
        assertEquals("world", result1)
        coVerify(exactly = 1) { dao.findBySourceText("hello") }

        // Second get -- should come from LRU, no additional Room call
        val result2 = cache.get("hello")
        assertEquals("world", result2)
        coVerify(exactly = 1) { dao.findBySourceText("hello") } // still just 1 call
    }
}
