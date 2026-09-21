package com.roomql.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class AggregateTest {

    @Test
    fun `count renders COUNT of the column`() {
        val result = query {
            from("users")
            groupBy(Column<String>("status", "users"))
            having { count(Column<String>("id", "users")) gt 0 }
        }
        assertEquals("SELECT * FROM users GROUP BY status HAVING COUNT(id) > ?", result.sql)
        assertEquals(listOf(0L), result.args.toList())
    }

    @Test
    fun `countAll renders COUNT star`() {
        val result = query {
            from("users")
            groupBy(Column<String>("status", "users"))
            having { countAll() gt 0 }
        }
        assertEquals("SELECT * FROM users GROUP BY status HAVING COUNT(*) > ?", result.sql)
    }

    @Test
    fun `sum renders SUM of the column`() {
        val result = query {
            from("orders")
            groupBy(Column<String>("customer_id", "orders"))
            having { sum(Column<Int>("amount", "orders")) gt 100 }
        }
        assertEquals("SELECT * FROM orders GROUP BY customer_id HAVING SUM(amount) > ?", result.sql)
        assertEquals(listOf(100), result.args.toList())
    }

    @Test
    fun `avg renders AVG of the column`() {
        val result = query {
            from("orders")
            groupBy(Column<String>("customer_id", "orders"))
            having { avg(Column<Int>("amount", "orders")) gt 50.0 }
        }
        assertEquals("SELECT * FROM orders GROUP BY customer_id HAVING AVG(amount) > ?", result.sql)
    }

    @Test
    fun `min renders MIN of the column`() {
        val result = query {
            from("orders")
            groupBy(Column<String>("customer_id", "orders"))
            having { min(Column<Int>("amount", "orders")) gt 0 }
        }
        assertEquals("SELECT * FROM orders GROUP BY customer_id HAVING MIN(amount) > ?", result.sql)
    }

    @Test
    fun `max renders MAX of the column`() {
        val result = query {
            from("orders")
            groupBy(Column<String>("customer_id", "orders"))
            having { max(Column<Int>("amount", "orders")) gt 0 }
        }
        assertEquals("SELECT * FROM orders GROUP BY customer_id HAVING MAX(amount) > ?", result.sql)
    }

    @Test
    fun `aggregate result can drive orderBy`() {
        val result = query {
            from("orders")
            groupBy(Column<String>("customer_id", "orders"))
            orderBy(count(Column<String>("id", "orders")), SortDirection.DESC)
        }
        assertEquals("SELECT * FROM orders GROUP BY customer_id ORDER BY COUNT(id) DESC", result.sql)
    }
}
