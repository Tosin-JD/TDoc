package com.tosin.docprocessor.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecentFileDaoTest {

    private lateinit var db: TDocDatabase
    private lateinit var dao: RecentFileDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TDocDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.recentFileDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(
        uri: String,
        fileName: String = uri.substringAfterLast('/'),
        lastOpened: Long = System.currentTimeMillis()
    ) = RecentFileEntity(
        uri = uri,
        fileName = fileName,
        mimeType = "text/plain",
        sizeBytes = 42L,
        lastOpened = lastOpened
    )

    @Test
    fun `insert is observed in recents flow`() = runTest {
        dao.upsert(entity("content://doc/one.txt", fileName = "one.txt", lastOpened = 100L))

        val recents = dao.observeRecentFiles().first()
        assertEquals(1, recents.size)
        assertEquals("one.txt", recents.single().fileName)
    }

    @Test
    fun `recents ordered by lastOpened descending`() = runTest {
        dao.upsert(entity("content://doc/older.txt", lastOpened = 100L))
        dao.upsert(entity("content://doc/newer.txt", lastOpened = 300L))
        dao.upsert(entity("content://doc/middle.txt", lastOpened = 200L))

        val recents = dao.observeRecentFiles().first()
        assertEquals(
            listOf("newer.txt", "middle.txt", "older.txt"),
            recents.map { it.fileName }
        )
    }

    @Test
    fun `upsert on same uri updates row instead of duplicating`() = runTest {
        dao.upsert(entity("content://doc/same.txt", fileName = "first.txt", lastOpened = 100L))
        dao.upsert(entity("content://doc/same.txt", fileName = "second.txt", lastOpened = 500L))

        val recents = dao.observeRecentFiles().first()
        assertEquals(1, recents.size)
        assertEquals("second.txt", recents.single().fileName)
    }

    @Test
    fun `deleteByUri removes only the target row`() = runTest {
        dao.upsert(entity("content://doc/keep.txt"))
        dao.upsert(entity("content://doc/drop.txt"))
        dao.deleteByUri("content://doc/drop.txt")

        val recents = dao.observeRecentFiles().first()
        assertEquals(listOf("keep.txt"), recents.map { it.fileName })
        assertNull(dao.getByUri("content://doc/drop.txt"))
    }

    @Test
    fun `prune keeps the 20 most recent rows`() = runTest {
        for (i in 1..25) {
            dao.upsert(entity("content://doc/f$i.txt", fileName = "f$i.txt", lastOpened = i.toLong()))
        }
        dao.prune()

        val recents = dao.observeRecentFiles().first()
        assertEquals(20, recents.size)
        // Newest 6..25 survive; the oldest 1..5 pruned.
        assertEquals("f25.txt", recents.first().fileName)
        assertEquals("f6.txt", recents.last().fileName)
        assertNull(dao.getByUri("content://doc/f1.txt"))
    }
}