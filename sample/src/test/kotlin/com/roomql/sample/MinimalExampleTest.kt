package com.roomql.sample

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Proves the copy-paste path in [MinimalExample.kt] actually runs.
 *
 * If a newcomer's first RoomQL query is going to be this file, the file has to work —
 * so this drives it against a real in-memory Room database rather than asserting on the
 * generated SQL string.
 */
@RunWith(RobolectricTestRunner::class)
class MinimalExampleTest {

    private lateinit var db: MinimalDatabase

    private val notes = listOf(
        NoteEntity(id = 1, title = "Dynamic SQL notes", archived = false),
        NoteEntity(id = 2, title = "Room migration notes", archived = false),
        NoteEntity(id = 3, title = "Archived SQL scratchpad", archived = true),
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MinimalDatabase::class.java,
        ).allowMainThreadQueries().build()
        db.noteDao().insertAll(notes)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `both filters null returns everything`() {
        val all = findNotes(db.noteDao(), titleFragment = null, archived = null)
        assertEquals(notes.size, all.size)
    }

    @Test
    fun `a null filter is omitted rather than matched against NULL`() {
        // archived = null must not mean "archived IS NULL" — it must drop the condition,
        // so both the archived and unarchived SQL notes come back.
        val bySql = findNotes(db.noteDao(), titleFragment = "SQL", archived = null)
        assertEquals(listOf(3, 1), bySql.map(NoteEntity::id))
    }

    @Test
    fun `each filter narrows, and they combine`() {
        val unarchived = findNotes(db.noteDao(), titleFragment = null, archived = false)
        assertEquals(listOf(1, 2), unarchived.map(NoteEntity::id))

        val both = findNotes(db.noteDao(), titleFragment = "SQL", archived = false)
        assertEquals(listOf(1), both.map(NoteEntity::id))
    }

    @Test
    fun `results come back in the requested order`() {
        val all = findNotes(db.noteDao(), titleFragment = null, archived = null)
        assertTrue(all.map(NoteEntity::title) == all.map(NoteEntity::title).sorted())
    }
}
