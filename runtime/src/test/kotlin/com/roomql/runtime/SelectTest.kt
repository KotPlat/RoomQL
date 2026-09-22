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
    fun `alias renders a quoted AS name`() {
        val result = query {
            from("categories")
            select(Column<Int>("id", "categories") alias "category_id")
        }
        assertEquals("SELECT id AS `category_id` FROM categories", result.sql)
    }

    @Test
    fun `select with grouped aggregate and matching groupBy column`() {
        val result = query {
            from("orders")
            groupBy(Column<String>("customer_id", "orders"))
            select(Column<String>("customer_id", "orders"), count(Column<Long>("id", "orders")) alias "order_count")
        }
        assertEquals("SELECT customer_id, COUNT(id) AS `order_count` FROM orders GROUP BY customer_id", result.sql)
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

    @Test
    fun `alias quoting lets a reserved word name a column`() {
        val result = query {
            from("products")
            select(Column<Int>("position", "products") alias "order")
        }
        assertEquals("SELECT position AS `order` FROM products", result.sql)
    }

    @Test
    fun `alias containing a backtick is rejected`() {
        assertFailsWith<RoomQlException> {
            query {
                from("products")
                select(Column<Int>("id", "products") alias "a`b")
            }
        }
    }

    @Test
    fun `two select items sharing an alias are rejected`() {
        assertFailsWith<RoomQlException> {
            query {
                from("products")
                select(Column<Int>("id", "products") alias "x", Column<String>("name", "products") alias "x")
            }
        }
    }

    @Test
    fun `an alias clashing with a bare column name is rejected`() {
        assertFailsWith<RoomQlException> {
            query {
                from("products")
                select(Column<Int>("id", "products"), Column<Int>("brand_id", "products") alias "id")
            }
        }
    }

    @Test
    fun `the same column name from two joined tables is rejected with an alias hint`() {
        val error = assertFailsWith<RoomQlException> {
            query {
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
                select(Column<Int>("id", "users"), Column<Int>("id", "orders"))
            }
        }
        assertEquals("select(...) returns more than one column named: id; alias all but one", error.message)
    }

    @Test
    fun `two identical unaliased aggregates are rejected`() {
        val error = assertFailsWith<RoomQlException> {
            query {
                from("orders")
                select(countAll(), countAll())
            }
        }
        assertEquals("select(...) returns more than one column named: COUNT(*); alias all but one", error.message)
    }

    @Test
    fun `different unaliased aggregates are allowed`() {
        val result = query {
            from("orders")
            select(countAll(), max(Column<Long>("total", "orders")))
        }
        assertEquals("SELECT COUNT(*), MAX(total) FROM orders", result.sql)
    }

    @Test
    fun `select items are covariant in their value type`() {
        val items: Array<SelectItem<Long?>> = arrayOf(countAll() alias "total")
        val result = query {
            from("orders")
            select(*items)
        }
        assertEquals("SELECT COUNT(*) AS `total` FROM orders", result.sql)
    }
}
