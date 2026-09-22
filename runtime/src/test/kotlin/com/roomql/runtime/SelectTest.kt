package com.roomql.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SelectTest {

    @Test
    fun `select absent renders SELECT star`() {
        val result = query { from("users") }
        assertEquals("SELECT * FROM users", result.sql)
    }

    @Test
    fun `select with a single aggregate renders no alias`() {
        val result = query {
            from("products")
            where { Column<String>("category", "products") eq "electronics" }
            select(countAll())
        }
        assertEquals("SELECT COUNT(*) FROM products WHERE category = ?", result.sql)
        assertEquals(listOf("electronics"), result.args.toList())
    }

    @Test
    fun `select with a bare column renders the column name`() {
        val result = query {
            from("users")
            select(Column<String>("status", "users"))
        }
        assertEquals("SELECT status FROM users", result.sql)
    }

    @Test
    fun `select with multiple bare columns renders an ordinary column list`() {
        val result = query {
            from("users")
            select(Column<Int>("id", "users"), Column<String>("status", "users"))
        }
        assertEquals("SELECT id, status FROM users", result.sql)
    }

    @Test
    fun `alias renders AS name`() {
        val result = query {
            from("categories")
            select(Column<Int>("id", "categories") alias "category_id")
        }
        assertEquals("SELECT id AS category_id FROM categories", result.sql)
    }

    @Test
    fun `select with grouped aggregate and matching groupBy column`() {
        val result = query {
            from("orders")
            groupBy(Column<String>("customer_id", "orders"))
            select(Column<String>("customer_id", "orders"), count(Column<Long>("id", "orders")) alias "order_count")
        }
        assertEquals("SELECT customer_id, COUNT(id) AS order_count FROM orders GROUP BY customer_id", result.sql)
    }

    @Test
    fun `select switches off join collision aliasing`() {
        val result = query {
            from(object : EntityTable {
                override val tableName = "users"
                override val allColumnNames = listOf("id", "name")
            })
            join(
                object : EntityTable {
                    override val tableName = "orders"
                    override val allColumnNames = listOf("id", "user_id")
                },
                JoinType.INNER,
            ) {}
            select(Column<Int>("id", "users"))
        }
        assertEquals("SELECT users.id FROM users INNER JOIN orders", result.sql)
    }

    @Test
    fun `grouped query rejects a bare column missing from groupBy`() {
        assertFailsWith<RoomQlException> {
            query {
                from("orders")
                groupBy(Column<String>("customer_id", "orders"))
                select(Column<String>("customer_id", "orders"), Column<String>("status", "orders"))
            }
        }
    }

    @Test
    fun `ungrouped query rejects mixing an aggregate with a bare column`() {
        assertFailsWith<RoomQlException> {
            query {
                from("orders")
                select(Column<String>("customer_id", "orders"), countAll())
            }
        }
    }
}
