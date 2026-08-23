package com.maelle.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maelle.data.local.database.MaelleDatabase
import com.maelle.data.local.entity.LibrarySectionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibrarySectionDaoTest {

    private lateinit var database: MaelleDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MaelleDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun section(
        serverId: String,
        sectionKey: String,
        title: String = "Section $sectionKey",
        type: String = "movie",
    ) = LibrarySectionEntity(
        serverId = serverId,
        sectionKey = sectionKey,
        title = title,
        type = type,
        composite = null,
        art = null,
        thumb = null,
        updatedAtEpochMs = 1_000L,
    )

    @Test
    fun upsertReplacesWithinServerButKeepsOtherServers() = runTest {
        val dao = database.librarySectionDao()
        dao.upsertAll(listOf(section("srv-1", "1", title = "Movies Old")))
        dao.upsertAll(
            listOf(
                section("srv-1", "1", title = "Movies New"),
                section("srv-2", "1", title = "Other Server Movies"),
            ),
        )

        val srv1 = dao.listByServer("srv-1")
        val srv2 = dao.listByServer("srv-2")

        assertEquals(1, srv1.size)
        assertEquals("Movies New", srv1.first().title)
        assertEquals(1, srv2.size)
        assertEquals("Other Server Movies", srv2.first().title)
    }

    @Test
    fun deleteByServerOnlyRemovesThatServersRows() = runTest {
        val dao = database.librarySectionDao()
        dao.upsertAll(
            listOf(
                section("srv-1", "1"),
                section("srv-1", "2"),
                section("srv-2", "1"),
            ),
        )

        dao.deleteByServer("srv-1")

        assertEquals(0, dao.listByServer("srv-1").size)
        assertEquals(1, dao.listByServer("srv-2").size)
    }

    @Test
    fun listByServerReturnsNullSafeEmptyForUnknownServer() = runTest {
        assertNull(null)
        assertEquals(0, database.librarySectionDao().listByServer("unknown").size)
    }
}
