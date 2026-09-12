package com.roomql.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `@RoomQlDsl` (see [RoomQlDsl]) is a compile-time guarantee, not a runtime one, so it can't be
 * asserted by a test here. Verified manually: pasting
 * ```
 * query { from("users"); where { limit(5) } }
 * ```
 * into this module fails `:runtime:compileKotlin` with
 * "'fun limit(n: Int): Unit' cannot be called in this context with an implicit receiver."
 * Re-check this by hand if `RoomQlDsl` is ever removed from a DSL scope class.
 *
 * Similarly, `like`/`notLike`/`contains` are constrained to `Column<T : String?>`, which is also
 * a compile-time-only guarantee. Verified manually: `Column<Int>("age", "users") like "A%"` fails
 * `:runtime:compileKotlin` with "Unresolved reference. ... receiver type mismatch."
 */
class QueryBuilderTest {

    // --- from() + bare SELECT ---

    @Test
    fun `bare from produces SELECT star FROM table`() {
        val result = query { from("users") }
        assertEquals("SELECT * FROM users", result.sql)
        assertEquals(0, result.args.size)
    }

    @Test
    fun `build throws RoomQlException when from is missing`() {
        assertFailsWith<RoomQlException> {
            query { /* no from() */ }
        }
    }

    // --- WHERE: eq / notEq ---

    @Test
    fun `eq produces WHERE clause with positional arg`() {
        val result = query {
            from("users")
            where { Column<Int>("age", "users") eq 18 }
        }
        assertEquals("SELECT * FROM users WHERE age = ?", result.sql)
        assertEquals(listOf(18), result.args.toList())
    }

    @Test
    fun `notEq produces != clause`() {
        val result = query {
            from("users")
            where { Column<String>("status", "users") notEq "inactive" }
        }
        assertEquals("SELECT * FROM users WHERE status != ?", result.sql)
    }

    @Test
    fun `null value skips eq condition`() {
        val age: Int? = null
        val result = query {
            from("users")
            where { Column<Int>("age", "users") eq age }
        }
        assertEquals("SELECT * FROM users", result.sql)
        assertEquals(0, result.args.size)
    }

    // --- WHERE: gt / gte / lt / lte ---

    @Test
    fun `gt produces greater-than clause`() {
        val result = query {
            from("users")
            where { Column<Int>("age", "users") gt 18 }
        }
        assertEquals("SELECT * FROM users WHERE age > ?", result.sql)
        assertEquals(listOf(18), result.args.toList())
    }

    @Test
    fun `gte produces gte clause`() {
        val result = query {
            from("users")
            where { Column<Int>("age", "users") gte 18 }
        }
        assertEquals("SELECT * FROM users WHERE age >= ?", result.sql)
    }

    @Test
    fun `lt produces lt clause`() {
        val result = query {
            from("users")
            where { Column<Int>("age", "users") lt 65 }
        }
        assertEquals("SELECT * FROM users WHERE age < ?", result.sql)
    }

    @Test
    fun `lte produces lte clause`() {
        val result = query {
            from("users")
            where { Column<Int>("age", "users") lte 65 }
        }
        assertEquals("SELECT * FROM users WHERE age <= ?", result.sql)
    }

    // --- WHERE: like / notLike / contains ---

    @Test
    fun `like produces LIKE clause`() {
        val result = query {
            from("users")
            where { Column<String>("name", "users") like "John%" }
        }
        assertEquals("SELECT * FROM users WHERE name LIKE ?", result.sql)
        assertEquals(listOf("John%"), result.args.toList())
    }

    @Test
    fun `notLike produces NOT LIKE clause`() {
        val result = query {
            from("users")
            where { Column<String>("name", "users") notLike "Bot%" }
        }
        assertEquals("SELECT * FROM users WHERE name NOT LIKE ?", result.sql)
    }

    @Test
    fun `contains wraps value in percent signs`() {
        val result = query {
            from("users")
            where { Column<String>("name", "users") contains "john" }
        }
        assertEquals("SELECT * FROM users WHERE name LIKE ?", result.sql)
        assertEquals(listOf("%john%"), result.args.toList())
    }

    // --- WHERE: isNull / isNotNull ---

    @Test
    fun `isNull produces IS NULL clause`() {
        val result = query {
            from("users")
            where { Column<String?>("email", "users").isNull() }
        }
        assertEquals("SELECT * FROM users WHERE email IS NULL", result.sql)
        assertEquals(0, result.args.size)
    }

    @Test
    fun `isNotNull produces IS NOT NULL clause`() {
        val result = query {
            from("users")
            where { Column<String?>("email", "users").isNotNull() }
        }
        assertEquals("SELECT * FROM users WHERE email IS NOT NULL", result.sql)
    }

    // --- WHERE: inList / notInList ---

    @Test
    fun `inList produces IN clause with multiple args`() {
        val result = query {
            from("users")
            where { Column<Int>("role_id", "users") inList listOf(1, 2, 3) }
        }
        assertEquals("SELECT * FROM users WHERE role_id IN (?,?,?)", result.sql)
        assertEquals(listOf(1, 2, 3), result.args.toList())
    }

    @Test
    fun `notInList produces NOT IN clause`() {
        val result = query {
            from("users")
            where { Column<Int>("role_id", "users") notInList listOf(99) }
        }
        assertEquals("SELECT * FROM users WHERE role_id NOT IN (?)", result.sql)
    }

    @Test
    fun `null inList skips condition`() {
        val ids: List<Int>? = null
        val result = query {
            from("users")
            where { Column<Int>("role_id", "users") inList ids }
        }
        assertEquals("SELECT * FROM users", result.sql)
    }

    @Test
    fun `empty inList skips condition`() {
        val result = query {
            from("users")
            where { Column<Int>("role_id", "users") inList emptyList() }
        }
        assertEquals("SELECT * FROM users", result.sql)
    }

    // --- WHERE: between ---

    @Test
    fun `between produces BETWEEN clause`() {
        val result = query {
            from("users")
            where { Column<Int>("age", "users").between(18, 65) }
        }
        assertEquals("SELECT * FROM users WHERE age BETWEEN ? AND ?", result.sql)
        assertEquals(listOf(18, 65), result.args.toList())
    }

    @Test
    fun `between with null lower skips condition`() {
        val result = query {
            from("users")
            where { Column<Int>("age", "users").between(null, 65) }
        }
        assertEquals("SELECT * FROM users", result.sql)
    }

    // --- WHERE: AND combinator (default) ---

    @Test
    fun `multiple conditions in where block are AND-combined`() {
        val result = query {
            from("users")
            where {
                Column<Int>("age", "users") gt 18
                Column<String>("status", "users") eq "active"
            }
        }
        assertEquals("SELECT * FROM users WHERE age > ? AND status = ?", result.sql)
        assertEquals(listOf(18, "active"), result.args.toList())
    }

    // --- WHERE: OR group ---

    @Test
    fun `or block produces OR-grouped conditions`() {
        val result = query {
            from("users")
            where {
                or {
                    Column<String>("role", "users") eq "admin"
                    Column<String>("role", "users") eq "mod"
                }
            }
        }
        assertEquals("SELECT * FROM users WHERE (role = ? OR role = ?)", result.sql)
        assertEquals(listOf("admin", "mod"), result.args.toList())
    }

    @Test
    fun `AND conditions mix with OR group`() {
        val result = query {
            from("users")
            where {
                Column<Int>("age", "users") gt 18
                or {
                    Column<String>("role", "users") eq "admin"
                    Column<String>("role", "users") eq "mod"
                }
            }
        }
        assertEquals("SELECT * FROM users WHERE age > ? AND (role = ? OR role = ?)", result.sql)
    }

    // --- ORDER BY ---

    @Test
    fun `orderBy produces ORDER BY clause`() {
        val result = query {
            from("users")
            orderBy(Column<String>("name", "users"), SortDirection.ASC)
        }
        assertEquals("SELECT * FROM users ORDER BY name ASC", result.sql)
    }

    @Test
    fun `orderBy DESC`() {
        val result = query {
            from("users")
            orderBy(Column<String>("created_at", "users"), SortDirection.DESC)
        }
        assertEquals("SELECT * FROM users ORDER BY created_at DESC", result.sql)
    }

    @Test
    fun `chained orderBy produces multi-column ORDER BY`() {
        val result = query {
            from("users")
            orderBy(Column<String>("last_name", "users"), SortDirection.ASC)
            orderBy(Column<String>("first_name", "users"), SortDirection.ASC)
        }
        assertEquals("SELECT * FROM users ORDER BY last_name ASC, first_name ASC", result.sql)
    }

    // --- LIMIT / OFFSET ---

    @Test
    fun `limit produces LIMIT clause`() {
        val result = query {
            from("users")
            limit(20)
        }
        assertEquals("SELECT * FROM users LIMIT 20", result.sql)
    }

    @Test
    fun `limit and offset together`() {
        val result = query {
            from("users")
            limit(20)
            offset(40)
        }
        assertEquals("SELECT * FROM users LIMIT 20 OFFSET 40", result.sql)
    }

    @Test
    fun `non-positive limit throws at build`() {
        assertFailsWith<RoomQlException> {
            query { from("users"); limit(0) }
        }
    }

    @Test
    fun `offset without limit throws at build`() {
        assertFailsWith<RoomQlException> {
            query { from("users"); offset(10) }
        }
    }

    // --- GROUP BY / HAVING ---

    @Test
    fun `groupBy produces GROUP BY clause`() {
        val result = query {
            from("users")
            groupBy(Column<String>("status", "users"))
        }
        assertEquals("SELECT * FROM users GROUP BY status", result.sql)
    }

    @Test
    fun `groupBy with having produces HAVING clause`() {
        val result = query {
            from("users")
            groupBy(Column<String>("status", "users"))
            having { Column<Int>("age", "users") gt 18 }
        }
        assertEquals("SELECT * FROM users GROUP BY status HAVING age > ?", result.sql)
        assertEquals(listOf(18), result.args.toList())
    }

    @Test
    fun `having without groupBy throws at build`() {
        assertFailsWith<RoomQlException> {
            query {
                from("users")
                having { Column<Int>("age", "users") gt 18 }
            }
        }
    }

    // --- Multi-call where { } merges ---

    @Test
    fun `multiple where calls merge into single AND list`() {
        val result = query {
            from("users")
            where { Column<Int>("age", "users") gt 18 }
            where { Column<String>("status", "users") eq "active" }
        }
        assertEquals("SELECT * FROM users WHERE age > ? AND status = ?", result.sql)
        assertEquals(listOf(18, "active"), result.args.toList())
    }

    // --- JOIN ---

    private fun tableOf(name: String, vararg cols: String) = object : EntityTable {
        override val tableName = name
        override val allColumnNames = cols.toList()
    }

    @Test
    fun `inner join produces INNER JOIN clause`() {
        val users = tableOf("users", "id", "name")
        val orders = tableOf("orders", "order_id", "user_id")
        val result = query {
            from(users)
            join(orders, JoinType.INNER) {
                on { Column<Long>("id", "users") eq Column<Long>("user_id", "orders") }
            }
        }
        assertEquals(
            "SELECT id, name, order_id, user_id FROM users INNER JOIN orders ON users.id = orders.user_id",
            result.sql
        )
    }

    @Test
    fun `left join produces LEFT JOIN clause`() {
        val users = tableOf("users", "id", "name")
        val profiles = tableOf("profiles", "profile_id", "bio")
        val result = query {
            from(users)
            join(profiles, JoinType.LEFT) {
                on { Column<Long>("id", "users") eq Column<Long>("profile_id", "profiles") }
            }
        }
        assertEquals(
            "SELECT id, name, profile_id, bio FROM users LEFT JOIN profiles ON users.id = profiles.profile_id",
            result.sql
        )
    }

    @Test
    fun `chained joins produce multiple JOIN clauses`() {
        val users = tableOf("users", "id", "name")
        val orders = tableOf("orders", "order_id", "user_id")
        val items = tableOf("items", "item_id", "order_ref")
        val result = query {
            from(users)
            join(orders, JoinType.INNER) {
                on { Column<Long>("id", "users") eq Column<Long>("user_id", "orders") }
            }
            join(items, JoinType.LEFT) {
                on { Column<Long>("order_id", "orders") eq Column<Long>("order_ref", "items") }
            }
        }
        assertEquals(
            "SELECT id, name, order_id, user_id, item_id, order_ref FROM users" +
                " INNER JOIN orders ON users.id = orders.user_id" +
                " LEFT JOIN items ON orders.order_id = items.order_ref",
            result.sql
        )
    }

    @Test
    fun `conflicting column names are aliased with table prefix`() {
        val users = tableOf("users", "id", "name")
        val orders = tableOf("orders", "id", "user_id", "amount")
        val result = query {
            from(users)
            join(orders, JoinType.INNER) {
                on { Column<Long>("id", "users") eq Column<Long>("user_id", "orders") }
            }
        }
        assertEquals(
            "SELECT users.id AS users__id, name, orders.id AS orders__id, user_id, amount" +
                " FROM users INNER JOIN orders ON users.id = orders.user_id",
            result.sql
        )
    }

    @Test
    fun `non-conflicting columns are not aliased`() {
        val users = tableOf("users", "id", "name")
        val addresses = tableOf("addresses", "address_id", "city")
        val result = query {
            from(users)
            join(addresses, JoinType.INNER) {
                on { Column<Long>("id", "users") eq Column<Long>("address_id", "addresses") }
            }
        }
        val sql = result.sql
        assertTrue("AS" !in sql, "Non-conflicting columns should not be aliased: $sql")
        assertTrue("SELECT id, name, address_id, city" in sql)
    }

    @Test
    fun `join with a raw string from throws instead of silently dropping aliasing`() {
        assertFailsWith<RoomQlException> {
            query {
                from("users")
                join(tableOf("orders", "id", "user_id"), JoinType.INNER) {
                    on { Column<Long>("id", "users") eq Column<Long>("user_id", "orders") }
                }
            }
        }
    }

    @Test
    fun `join combined with where clause`() {
        val users = tableOf("users", "id", "name")
        val orders = tableOf("orders", "order_id", "user_id", "status")
        val result = query {
            from(users)
            join(orders, JoinType.INNER) {
                on { Column<Long>("id", "users") eq Column<Long>("user_id", "orders") }
            }
            where { Column<String>("status", "orders") eq "active" }
        }
        assertEquals(
            "SELECT id, name, order_id, user_id, status" +
                " FROM users INNER JOIN orders ON users.id = orders.user_id" +
                " WHERE status = ?",
            result.sql
        )
        assertEquals(listOf("active"), result.args.toList())
    }

    @Test
    fun `where on a colliding column name is qualified when joined`() {
        val users = tableOf("users", "id", "name", "status")
        val orders = tableOf("orders", "id", "user_id", "status")
        val result = query {
            from(users)
            join(orders, JoinType.INNER) {
                on { Column<Long>("id", "users") eq Column<Long>("user_id", "orders") }
            }
            where { Column<String>("status", "orders") eq "active" }
        }
        assertEquals(
            "SELECT users.id AS users__id, name, users.status AS users__status," +
                " orders.id AS orders__id, user_id, orders.status AS orders__status" +
                " FROM users INNER JOIN orders ON users.id = orders.user_id" +
                " WHERE orders.status = ?",
            result.sql
        )
        assertEquals(listOf("active"), result.args.toList())
    }

    @Test
    fun `groupBy and orderBy on a colliding column name are qualified when joined`() {
        val users = tableOf("users", "id", "name")
        val orders = tableOf("orders", "id", "user_id")
        val result = query {
            from(users)
            join(orders, JoinType.INNER) {
                on { Column<Long>("id", "users") eq Column<Long>("user_id", "orders") }
            }
            groupBy(Column<Long>("id", "users"))
            orderBy(Column<Long>("id", "orders"), SortDirection.ASC)
        }
        assertTrue("GROUP BY users.id" in result.sql)
        assertTrue("ORDER BY orders.id ASC" in result.sql)
    }

    @Test
    fun `having on a colliding column name is qualified when joined`() {
        val users = tableOf("users", "id", "name")
        val orders = tableOf("orders", "id", "user_id")
        val result = query {
            from(users)
            join(orders, JoinType.INNER) {
                on { Column<Long>("id", "users") eq Column<Long>("user_id", "orders") }
            }
            groupBy(Column<Long>("id", "users"))
            having { Column<Long>("id", "orders") gt 5L }
        }
        assertTrue("HAVING orders.id > ?" in result.sql)
    }

    @Test
    fun `non-colliding columns stay unqualified when joined`() {
        val users = tableOf("users", "id", "name")
        val orders = tableOf("orders", "order_id", "user_id", "amount")
        val result = query {
            from(users)
            join(orders, JoinType.INNER) {
                on { Column<Long>("id", "users") eq Column<Long>("user_id", "orders") }
            }
            where { Column<Double>("amount", "orders") gt 100.0 }
        }
        assertTrue("WHERE amount > ?" in result.sql)
    }

    // --- Full query combining multiple clauses ---

    @Test
    fun `full query with where orderBy limit offset`() {
        val result = query {
            from("users")
            where {
                Column<Int>("age", "users") gt 18
                Column<String>("status", "users") eq "active"
            }
            orderBy(Column<String>("name", "users"), SortDirection.ASC)
            limit(20)
            offset(40)
        }
        assertEquals(
            "SELECT * FROM users WHERE age > ? AND status = ? ORDER BY name ASC LIMIT 20 OFFSET 40",
            result.sql
        )
        assertEquals(listOf(18, "active"), result.args.toList())
    }
}
