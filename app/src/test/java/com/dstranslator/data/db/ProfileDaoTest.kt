package com.dstranslator.data.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Tests for ProfileDao CRUD operations using a FakeProfileDao backed by
 * an in-memory list. Verifies the DAO contract without requiring Room/Robolectric.
 */
class ProfileDaoTest {

    private lateinit var dao: FakeProfileDao

    @Before
    fun setup() {
        dao = FakeProfileDao()
    }

    @Test
    fun `getDefault returns profile with isDefault true`() = runTest {
        val defaultProfile = ProfileEntity(
            id = 1,
            name = "Default",
            isDefault = true,
            settingsJson = "{}",
            captureRegionsJson = "[]"
        )
        val otherProfile = ProfileEntity(
            id = 2,
            name = "Other",
            isDefault = false,
            settingsJson = "{}",
            captureRegionsJson = "[]"
        )
        dao.insert(defaultProfile)
        dao.insert(otherProfile)

        val result = dao.getDefault()
        assertNotNull(result)
        assertEquals("Default", result!!.name)
        assertEquals(true, result.isDefault)
    }

    @Test
    fun `getDefault returns null when no default profile exists`() = runTest {
        val profile = ProfileEntity(
            id = 1,
            name = "Not Default",
            isDefault = false,
            settingsJson = "{}",
            captureRegionsJson = "[]"
        )
        dao.insert(profile)
        assertNull(dao.getDefault())
    }

    @Test
    fun `insert and getById roundtrip`() = runTest {
        val profile = ProfileEntity(
            id = 0, // autoGenerate
            name = "Game Profile",
            isDefault = false,
            settingsJson = """{"translationEngine":"deepl"}""",
            captureRegionsJson = "[]",
            autoReadEnabled = true,
            autoReadFlushMode = false
        )
        val insertedId = dao.insert(profile)

        val retrieved = dao.getById(insertedId)
        assertNotNull(retrieved)
        assertEquals("Game Profile", retrieved!!.name)
        assertEquals("""{"translationEngine":"deepl"}""", retrieved.settingsJson)
        assertEquals(true, retrieved.autoReadEnabled)
        assertEquals(false, retrieved.autoReadFlushMode)
    }

    @Test
    fun `deleteById removes profile and getById returns null`() = runTest {
        val profile = ProfileEntity(
            id = 0,
            name = "To Delete",
            isDefault = false,
            settingsJson = "{}",
            captureRegionsJson = "[]"
        )
        val insertedId = dao.insert(profile)
        assertNotNull(dao.getById(insertedId))

        dao.deleteById(insertedId)
        assertNull(dao.getById(insertedId))
    }

    @Test
    fun `count returns correct number of profiles`() = runTest {
        assertEquals(0, dao.count())

        dao.insert(ProfileEntity(id = 0, name = "P1", settingsJson = "{}", captureRegionsJson = "[]"))
        dao.insert(ProfileEntity(id = 0, name = "P2", settingsJson = "{}", captureRegionsJson = "[]"))
        dao.insert(ProfileEntity(id = 0, name = "P3", settingsJson = "{}", captureRegionsJson = "[]"))
        assertEquals(3, dao.count())

        // Delete one
        val all = dao.getAll().first()
        dao.deleteById(all.first().id)
        assertEquals(2, dao.count())
    }

    @Test
    fun `getAll returns profiles ordered by updatedAt desc`() = runTest {
        dao.insert(ProfileEntity(id = 0, name = "Old", settingsJson = "{}", captureRegionsJson = "[]", updatedAt = 1000))
        dao.insert(ProfileEntity(id = 0, name = "New", settingsJson = "{}", captureRegionsJson = "[]", updatedAt = 3000))
        dao.insert(ProfileEntity(id = 0, name = "Mid", settingsJson = "{}", captureRegionsJson = "[]", updatedAt = 2000))

        val all = dao.getAll().first()
        assertEquals(3, all.size)
        assertEquals("New", all[0].name)
        assertEquals("Mid", all[1].name)
        assertEquals("Old", all[2].name)
    }
}

/**
 * In-memory fake implementation of ProfileDao for unit testing.
 * Simulates auto-increment IDs and all CRUD operations.
 */
class FakeProfileDao : ProfileDao {
    private val profiles = mutableListOf<ProfileEntity>()
    private var nextId = 1L
    private val _flow = MutableStateFlow<List<ProfileEntity>>(emptyList())

    override fun getAll(): Flow<List<ProfileEntity>> {
        return _flow.map { it.sortedByDescending { p -> p.updatedAt } }
    }

    override suspend fun getById(id: Long): ProfileEntity? {
        return profiles.find { it.id == id }
    }

    override suspend fun getDefault(): ProfileEntity? {
        return profiles.find { it.isDefault }
    }

    override suspend fun insert(profile: ProfileEntity): Long {
        val id = if (profile.id == 0L) nextId++ else profile.id
        val toInsert = profile.copy(id = id)
        profiles.add(toInsert)
        _flow.value = profiles.toList()
        return id
    }

    override suspend fun update(profile: ProfileEntity) {
        val index = profiles.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            profiles[index] = profile
            _flow.value = profiles.toList()
        }
    }

    override suspend fun deleteById(id: Long) {
        profiles.removeAll { it.id == id }
        _flow.value = profiles.toList()
    }

    override suspend fun count(): Int {
        return profiles.size
    }
}
