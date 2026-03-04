package com.dstranslator.data.dictionary

import com.dstranslator.data.db.JMdictDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class JlptLevelTest {

    private lateinit var dao: JMdictDao
    private lateinit var repository: JMdictRepository

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
        repository = JMdictRepository(dao)
    }

    @Test
    fun `JLPT N5 level is 5`() = runTest {
        coEvery { dao.getJlptLevelForWord("食べる") } returns 5
        assertEquals(5, repository.getJlptLevel("食べる"))
    }

    @Test
    fun `JLPT N4 level is 4`() = runTest {
        coEvery { dao.getJlptLevelForWord("経験") } returns 4
        assertEquals(4, repository.getJlptLevel("経験"))
    }

    @Test
    fun `JLPT N3 level is 3`() = runTest {
        coEvery { dao.getJlptLevelForWord("政治") } returns 3
        assertEquals(3, repository.getJlptLevel("政治"))
    }

    @Test
    fun `JLPT N2 level is 2`() = runTest {
        coEvery { dao.getJlptLevelForWord("抽象") } returns 2
        assertEquals(2, repository.getJlptLevel("抽象"))
    }

    @Test
    fun `JLPT N1 level is 1`() = runTest {
        coEvery { dao.getJlptLevelForWord("憂鬱") } returns 1
        assertEquals(1, repository.getJlptLevel("憂鬱"))
    }

    @Test
    fun `unclassified word returns null`() = runTest {
        coEvery { dao.getJlptLevelForWord("未知の言葉") } returns null
        assertNull(repository.getJlptLevel("未知の言葉"))
    }

    @Test
    fun `getJlptLevel delegates to DAO getJlptLevelForWord`() = runTest {
        coEvery { dao.getJlptLevelForWord("走る") } returns 5

        repository.getJlptLevel("走る")

        coVerify(exactly = 1) { dao.getJlptLevelForWord("走る") }
    }

    @Test
    fun `getJlptLevel blank input does not call DAO`() = runTest {
        repository.getJlptLevel("")

        coVerify(exactly = 0) { dao.getJlptLevelForWord(any()) }
    }
}
