package com.roomql.runtime

import java.nio.ByteBuffer
import java.util.Date
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CrashGuardTest {

    private enum class Status { ACTIVE, BANNED }

    private fun tableOf(name: String, vararg cols: String) = object : EntityTable {
        override val tableName = name
        override val allColumnNames = cols.toList()
    }

    private val users = tableOf("users", "id", "name", "status")
    private val orders = tableOf("orders", "id", "user_id", "amount")

    // --- #88: values Room stores in a different form ---

    @Test
    fun `enum binds its name, as Room stores it`() {
        val result = query { from("users"); where { Column<Status>("status", "users") eq Status.ACTIVE } }
        assertEquals(listOf<Any?>("ACTIVE"), result.args)
    }

    @Test
    fun `enum list binds names rather than being inlined`() {
        val result = query {
            from("users")
            where { Column<Status>("status", "users") inList listOf(Status.ACTIVE, Status.BANNED) }
        }
        assertEquals("SELECT * FROM `users` WHERE `status` IN (?,?)", result.sql)
        assertEquals(listOf<Any?>("ACTIVE", "BANNED"), result.args)
    }

    @Test
    fun `UUID binds Room's 16-byte big-endian form`() {
        val uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val result = query { from("users"); where { Column<UUID>("uid", "users") eq uuid } }
        val expected = ByteBuffer.allocate(16).putLong(uuid.mostSignificantBits).putLong(uuid.leastSignificantBits).array()
        assertContentEquals(expected, result.args.single() as ByteArray)
    }

    @Test
    fun `Char binds its code point`() {
        val result = query { from("users"); where { Column<Char>("grade", "users") eq 'A' } }
        assertEquals(listOf<Any?>(65), result.args)
    }

    @Test
    fun `a type only a TypeConverter can store throws naming the column`() {
        val e = assertFailsWith<RoomQlException> {
            query { from("users"); where { Column<Date>("created", "users") gt Date(0) } }
        }
        assertTrue("java.util.Date" in e.message!! && "users.created" in e.message!!, e.message)
    }

    @Test
    fun `a TypeConverter type in having throws too`() {
        assertFailsWith<RoomQlException> {
            query {
                from("users")
                groupBy(Column<String>("name", "users"))
                having { max(Column<Date>("created", "users")) gt Date(0) }
            }
        }
    }

    // --- #89: identifiers are quoted ---

    @Test
    fun `reserved-word table and column names are quoted`() {
        val result = query {
            from(tableOf("order", "group"))
            where { Column<String>("group", "order") eq "x" }
            orderBy(Column<String>("group", "order"), SortDirection.ASC)
        }
        assertEquals("SELECT * FROM `order` WHERE `group` = ? ORDER BY `group` ASC", result.sql)
    }

    @Test
    fun `join ON and collision aliases are quoted`() {
        val result = query {
            from(users)
            join(orders, JoinType.LEFT) { on { Column<Long>("id", "users") eq Column<Long>("user_id", "orders") } }
        }
        assertTrue("`users`.`id` AS `users__id`" in result.sql, result.sql)
        assertTrue("LEFT JOIN `orders` ON `users`.`id` = `orders`.`user_id`" in result.sql, result.sql)
    }

    @Test
    fun `a backtick in a table name is rejected`() {
        assertFailsWith<RoomQlException> { query { from("us`ers") } }
    }

    // --- #90: bind-argument cap ---

    @Test
    fun `999 bound values are allowed`() {
        val result = query { from("users"); where { Column<String>("name", "users") inList List(999) { "n$it" } } }
        assertEquals(999, result.args.size)
    }

    @Test
    fun `more than 999 bound values throws`() {
        val e = assertFailsWith<RoomQlException> {
            query { from("users"); where { Column<String>("name", "users") inList List(1000) { "n$it" } } }
        }
        assertTrue("1000" in e.message!!, e.message)
    }

    @Test
    fun `a large integer list is inlined and stays under the cap`() {
        val result = query { from("users"); where { Column<Long>("id", "users") inList List(5000) { it.toLong() } } }
        assertEquals(0, result.args.size)
    }

    // --- #92: self-join ---

    @Test
    fun `joining a table to itself throws`() {
        val e = assertFailsWith<RoomQlException> {
            query {
                from(users)
                join(users, JoinType.LEFT) { on { Column<Long>("id", "users") eq Column<Long>("id", "users") } }
            }
        }
        assertTrue("users" in e.message!!, e.message)
    }

    // --- #93: columns from a table outside from/join ---

    @Test
    fun `a where column from an unjoined table throws`() {
        val e = assertFailsWith<RoomQlException> {
            query { from(users); where { Column<Double>("amount", "orders") gt 1.0 } }
        }
        assertTrue("orders.amount" in e.message!!, e.message)
    }

    @Test
    fun `select, groupBy, having and orderBy are checked too`() {
        val foreign = Column<Double>("amount", "orders")
        assertFailsWith<RoomQlException> { query { from(users); select(foreign) } }
        assertFailsWith<RoomQlException> { query { from(users); select(sum(foreign)) } }
        assertFailsWith<RoomQlException> { query { from(users); groupBy(foreign) } }
        assertFailsWith<RoomQlException> {
            query { from(users); groupBy(Column<String>("name", "users")); having { foreign gt 1.0 } }
        }
        assertFailsWith<RoomQlException> { query { from(users); orderBy(foreign, SortDirection.ASC) } }
    }

    @Test
    fun `a column from a joined table is accepted`() {
        query {
            from(users)
            join(orders, JoinType.INNER) { on { Column<Long>("id", "users") eq Column<Long>("user_id", "orders") } }
            where { Column<Double>("amount", "orders") gt 1.0 }
        }
    }

    @Test
    fun `from(String) skips the table check, having no metadata`() {
        query { from("users"); where { Column<Double>("amount", "orders") gt 1.0 } }
    }
}
