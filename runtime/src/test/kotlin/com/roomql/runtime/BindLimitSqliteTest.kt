package com.roomql.runtime

import org.sqlite.SQLiteConnection
import org.sqlite.SQLiteLimits
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Runs RoomQL's SQL on a real SQLite capped at 999 bind arguments, the limit on Android API 30 and below. */
class BindLimitSqliteTest {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:") as SQLiteConnection
        connection.setLimit(SQLiteLimits.SQLITE_LIMIT_VARIABLE_NUMBER, 999)
        connection.createStatement().use { it.execute("CREATE TABLE users (id INTEGER, name TEXT)") }
        connection.createStatement().use { it.execute("INSERT INTO users VALUES (1, 'n1'), (4999, 'n4999')") }
    }

    @AfterTest
    fun tearDown() = connection.close()

    private fun count(query: RoomQlQuery): Int = connection.prepareStatement(query.sql).use { statement ->
        query.args.forEachIndexed { i, arg -> statement.setObject(i + 1, arg) }
        statement.executeQuery().use { rows -> generateSequence { if (rows.next()) Unit else null }.count() }
    }

    @Test
    fun `the cap is real - 1000 bound values fail in SQLite itself`() {
        val raw = RoomQlQuery("SELECT * FROM users WHERE name IN (${List(1000) { "?" }.joinToString(",")})", List(1000) { "n$it" })
        val e = assertFailsWith<SQLException> { count(raw) }
        assertTrue("too many SQL variables" in e.message!!, e.message)
    }

    @Test
    fun `999 bound strings run`() {
        assertEquals(1, count(query { from("users"); where { Column<String>("name", "users") inList List(999) { "n$it" } } }))
    }

    @Test
    fun `5000 integer ids run, because they're inlined`() {
        assertEquals(2, count(query { from("users"); where { Column<Long>("id", "users") inList List(5000) { it.toLong() } } }))
    }

    @Test
    fun `1000 bound strings are left for SQLite to reject`() {
        val e = assertFailsWith<SQLException> {
            count(query { from("users"); where { Column<String>("name", "users") inList List(1000) { "n$it" } } })
        }
        assertTrue("too many SQL variables" in e.message!!, e.message)
    }
}
