package com.dstranslator.data.dictionary

import com.dstranslator.data.db.DictionaryLookupResult
import com.dstranslator.data.db.JMdictDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JMdictRepositoryTest {

    private lateinit var dao: JMdictDao
    private lateinit var repository: JMdictRepository

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
        repository = JMdictRepository(dao)
    }

    @Test
    fun `lookupWord blankInput returnsEmpty`() = runTest {
        val result = repository.lookupWord("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `lookupWord whitespaceInput returnsEmpty`() = runTest {
        val result = repository.lookupWord("   ")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `lookupWord parsesJsonArrayFields`() = runTest {
        coEvery { dao.lookupWord("食べる") } returns listOf(
            DictionaryLookupResult(
                entSeq = 1358280,
                kanji = """["食べる"]""",
                kana = """["たべる"]""",
                glosses = """["to eat","to live on"]""",
                pos = """["v1","vt"]""",
                jlpt = 5
            )
        )

        val results = repository.lookupWord("食べる")

        assertEquals(1, results.size)
        val result = results[0]
        assertEquals(1358280, result.entSeq)
        assertEquals(listOf("食べる"), result.kanji)
        assertEquals(listOf("たべる"), result.kana)
        assertEquals(listOf("to eat", "to live on"), result.glosses)
        assertEquals(listOf("v1", "vt"), result.partOfSpeech)
        assertEquals(5, result.jlptLevel)
    }

    @Test
    fun `lookupWord handlesPlainStringFallback`() = runTest {
        coEvery { dao.lookupWord("test") } returns listOf(
            DictionaryLookupResult(
                entSeq = 12345,
                kanji = "テスト",
                kana = "てすと",
                glosses = "test",
                pos = "noun",
                jlpt = null
            )
        )

        val results = repository.lookupWord("test")

        assertEquals(1, results.size)
        val result = results[0]
        assertEquals(listOf("テスト"), result.kanji)
        assertEquals(listOf("てすと"), result.kana)
        assertEquals(listOf("test"), result.glosses)
        assertEquals(listOf("noun"), result.partOfSpeech)
        assertNull(result.jlptLevel)
    }

    @Test
    fun `getJlptLevel returnsLevelFromDao`() = runTest {
        coEvery { dao.getJlptLevelForWord("食べる") } returns 5

        val level = repository.getJlptLevel("食べる")

        assertEquals(5, level)
    }

    @Test
    fun `getJlptLevel blankWord returnsNull`() = runTest {
        val level = repository.getJlptLevel("")
        assertNull(level)
    }

    @Test
    fun `getJlptLevel whitespace returnsNull`() = runTest {
        val level = repository.getJlptLevel("   ")
        assertNull(level)
    }

    @Test
    fun `parseJsonArray parsesValidJsonArray`() {
        val result = repository.parseJsonArray("""["a","b","c"]""")
        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test
    fun `parseJsonArray emptyArray returnsEmptyList`() {
        val result = repository.parseJsonArray("[]")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseJsonArray invalidJson returnsSingleElement`() {
        val result = repository.parseJsonArray("not json")
        assertEquals(listOf("not json"), result)
    }
}
