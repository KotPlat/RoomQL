package com.roomql.demo.search

import com.roomql.demo.data.OverloadedProductDao
import com.roomql.demo.data.ProductEntity

/**
 * Baseline 2: one DAO method per filter combination, selected by a dispatch tree.
 *
 * Every individual query is clean, minimal, and verified by Room at compile time -- this
 * approach produces the best SQL of the four. The cost is everything around it: 16 DAO
 * methods for four filters, plus the 16-branch dispatch below, and both double with each
 * new filter. Adding "brand" to this screen means writing 16 more methods and 16 more
 * branches, by hand, correctly.
 *
 * That is the 2^n problem RoomQL exists to remove, and this file is what it looks like.
 */
class OverloadedSearch(private val dao: OverloadedProductDao) : SearchStrategy {

    override val label = "Overloaded DAO"

    override val summary =
        "One @Query method per filter combination: 16 for four filters, 32 for five. " +
            "The SQL is perfect; the maintenance burden doubles with every filter."

    override val daoMethods = 16

    override val safety = "Every query verified by Room — and there are sixteen of them."

    override val callSite = """
        // 16 @Query methods in the DAO, plus this 16-branch dispatch:
        when {
            c != null && p != null && r != null && s != null ->
                dao.searchByCategoryAndMinPriceAndMinRatingAndInStock(c, p, r, s)
            c != null && p != null && r != null ->
                dao.searchByCategoryAndMinPriceAndMinRating(c, p, r)
            // ... 13 more branches ...
            else -> dao.searchNoFilters()
        }
    """.trimIndent()

    override fun search(filters: SearchFilters): SearchResult {
        val c = filters.category
        val p = filters.minPrice
        val r = filters.minRating
        val s = filters.inStockOnly

        val products: List<ProductEntity> = when {
            c != null && p != null && r != null && s != null ->
                dao.searchByCategoryAndMinPriceAndMinRatingAndInStock(c, p, r, s)
            c != null && p != null && r != null ->
                dao.searchByCategoryAndMinPriceAndMinRating(c, p, r)
            c != null && p != null && s != null ->
                dao.searchByCategoryAndMinPriceAndInStock(c, p, s)
            c != null && r != null && s != null ->
                dao.searchByCategoryAndMinRatingAndInStock(c, r, s)
            p != null && r != null && s != null ->
                dao.searchByMinPriceAndMinRatingAndInStock(p, r, s)
            c != null && p != null -> dao.searchByCategoryAndMinPrice(c, p)
            c != null && r != null -> dao.searchByCategoryAndMinRating(c, r)
            c != null && s != null -> dao.searchByCategoryAndInStock(c, s)
            p != null && r != null -> dao.searchByMinPriceAndMinRating(p, r)
            p != null && s != null -> dao.searchByMinPriceAndInStock(p, s)
            r != null && s != null -> dao.searchByMinRatingAndInStock(r, s)
            c != null -> dao.searchByCategory(c)
            p != null -> dao.searchByMinPrice(p)
            r != null -> dao.searchByMinRating(r)
            s != null -> dao.searchByInStock(s)
            else -> dao.searchNoFilters()
        }

        return SearchResult(products = products, sql = describeSql(filters))
    }

    /** Reconstructs the SQL of whichever method the dispatch above selected. */
    private fun describeSql(filters: SearchFilters): String {
        val clauses = buildList {
            filters.category?.let { add("category = ?") }
            filters.minPrice?.let { add("price >= ?") }
            filters.minRating?.let { add("rating >= ?") }
            filters.inStockOnly?.let { add("in_stock = ?") }
        }
        val where = if (clauses.isEmpty()) "" else " WHERE ${clauses.joinToString(" AND ")}"
        return "SELECT * FROM products$where ORDER BY rating DESC, id ASC" +
            "\n-- from ${dispatchName(filters)}(), 1 of 16 hand-written methods"
    }

    private fun dispatchName(filters: SearchFilters): String {
        val parts = buildList {
            filters.category?.let { add("Category") }
            filters.minPrice?.let { add("MinPrice") }
            filters.minRating?.let { add("MinRating") }
            filters.inStockOnly?.let { add("InStock") }
        }
        return if (parts.isEmpty()) "searchNoFilters" else "searchBy${parts.joinToString("And")}"
    }
}
