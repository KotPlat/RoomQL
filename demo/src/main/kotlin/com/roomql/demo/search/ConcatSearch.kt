package com.roomql.demo.search

import androidx.sqlite.db.SimpleSQLiteQuery
import com.roomql.demo.data.ProductDao

/**
 * Baseline 3: build the SQL by hand and bind the arguments yourself.
 *
 * This is the closest baseline to what RoomQL does internally, and it produces the same
 * minimal SQL -- so it is a fair comparison rather than a strawman. What you give up is
 * every safety property: column names are strings the compiler never checks, so a rename
 * fails silently at runtime; the WHERE/AND assembly is easy to get wrong by one clause;
 * and the argument list must be built in exactly the same order as the placeholders, by
 * hand, every time. Get that order wrong and the query still runs -- it just lies.
 */
class ConcatSearch(private val dao: ProductDao) : SearchStrategy {

    override val label = "String concat"

    override val summary =
        "Hand-built SQL and a hand-ordered argument list. Same SQL as RoomQL, none of " +
            "the safety: unchecked column names and manual placeholder ordering."

    override val daoMethods = 1

    override val safety = "Nothing is checked. A renamed column fails at runtime, not build time."

    override val callSite = """
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any>()
        // every pair must stay in lockstep — one clause, one arg, same order
        filters.category?.let    { clauses += "category = ?"; args += it }
        filters.minPrice?.let    { clauses += "price >= ?";   args += it }
        filters.minRating?.let   { clauses += "rating >= ?";  args += it }
        filters.inStockOnly?.let { clauses += "in_stock = ?"; args += it }
        SimpleSQLiteQuery(sql, args.toTypedArray())
    """.trimIndent()

    override fun search(filters: SearchFilters): SearchResult {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any>()

        // Every pair below must stay in lockstep: one clause, one argument, same order.
        filters.category?.let { clauses += "category = ?"; args += it }
        filters.minPrice?.let { clauses += "price >= ?"; args += it }
        filters.minRating?.let { clauses += "rating >= ?"; args += it }
        filters.inStockOnly?.let { clauses += "in_stock = ?"; args += it }

        val sql = buildString {
            append("SELECT * FROM products")
            if (clauses.isNotEmpty()) {
                append(" WHERE ")
                append(clauses.joinToString(" AND "))
            }
            append(" ORDER BY rating DESC, id ASC")
        }

        val products = dao.search(SimpleSQLiteQuery(sql, args.toTypedArray()))
        return SearchResult(products = products, sql = sql)
    }
}
