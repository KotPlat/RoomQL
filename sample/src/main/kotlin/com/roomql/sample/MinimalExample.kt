package com.roomql.sample

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.RawQuery
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import com.roomql.android.toQuery
import com.roomql.runtime.SortDirection
import com.roomql.runtime.query

/*
 * The smallest complete RoomQL setup: one entity, one DAO, one database, one query.
 *
 * Everything RoomQL needs is in this file, in the order you would write it. Copy it into
 * your own project, rename the entity, and you have a working dynamic query.
 */

/**
 * 1. An ordinary Room entity. Nothing here is RoomQL-specific.
 *
 * At build time RoomQL's KSP processor reads this and generates an object called
 * `NoteEntityTable` in this same package, holding a typed `Column<T>` per property:
 * `NoteEntityTable.title` is a `Column<String>`, `NoteEntityTable.archived` a
 * `Column<Boolean>`. You never write that object by hand, and because each column is a
 * real Kotlin symbol, renaming a property below breaks the query at *compile* time
 * instead of silently at runtime.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val archived: Boolean,
)

/**
 * 2. A DAO using Room's own `@RawQuery`.
 *
 * `@RawQuery` is what lets the SQL be decided at runtime — it takes a
 * [SupportSQLiteQuery] instead of a fixed `@Query` string. This is the one place RoomQL
 * plugs into Room, and it is plain Room API: no RoomQL annotation, no code generation on
 * the DAO itself.
 */
@Dao
interface NoteDao {
    @Insert
    fun insertAll(notes: List<NoteEntity>)

    @RawQuery
    fun search(query: SupportSQLiteQuery): List<NoteEntity>
}

/** 3. An ordinary Room database. Again, nothing RoomQL-specific. */
@Database(entities = [NoteEntity::class], version = 1, exportSchema = false)
abstract class MinimalDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
}

/**
 * 4. The query — the only part that is actually RoomQL.
 *
 * Both parameters are optional, and this is the whole point: when a value is `null` its
 * condition is **left out of the generated SQL entirely**. It does not become
 * `title LIKE NULL`, and there is no `(:title IS NULL OR ...)` trick and no `if` ladder.
 * One function therefore covers all four combinations of the two filters:
 *
 * ```
 * search(null,  null) -> SELECT * FROM notes ORDER BY title ASC
 * search("sql", null) -> SELECT * FROM notes WHERE title LIKE ? ORDER BY title ASC
 * search(null, false) -> SELECT * FROM notes WHERE archived = ? ORDER BY title ASC
 * search("sql", false) -> SELECT * FROM notes WHERE title LIKE ? AND archived = ? ORDER BY title ASC
 * ```
 *
 * Values are always bound as positional `?` parameters, never concatenated into the SQL,
 * so there is no injection surface.
 *
 * Note the `.toQuery()` at the end: `query { }` returns a plain-JVM [com.roomql.runtime.RoomQlQuery]
 * (which is what makes it unit-testable without an emulator), and `.toQuery()` adapts it
 * into the [SupportSQLiteQuery] Room wants. That one call is the whole Android bridge.
 */
fun findNotes(dao: NoteDao, titleFragment: String?, archived: Boolean?): List<NoteEntity> {
    val q = query {
        from(NoteEntityTable)
        where {
            NoteEntityTable.title contains titleFragment
            NoteEntityTable.archived eq archived
        }
        orderBy(NoteEntityTable.title, SortDirection.ASC)
    }
    return dao.search(q.toQuery())
}
