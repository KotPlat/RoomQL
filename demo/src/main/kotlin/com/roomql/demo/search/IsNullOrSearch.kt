package com.roomql.demo.search

import com.roomql.demo.data.IsNullOrProductDao

/**
 * Baseline 1: a single `@Query` using `(:x IS NULL OR col = :x)` for every filter.
 *
 * Room verifies this SQL at compile time, which RoomQL cannot do -- a genuine advantage,
 * and the reason this approach is so common. The costs: the statement never shrinks, so
 * every query carries all four null checks whether or not the filters are used; the SQL
 * is hard to read; and adding a fifth filter means editing a growing string by hand.
 */
class IsNullOrSearch(private val dao: IsNullOrProductDao) : SearchStrategy {

    override val label = "IS NULL OR"

    override val summary =
        "One @Query with a (:x IS NULL OR col = :x) clause per filter. Compile-time " +
            "verified by Room, but the SQL never shrinks and barely reads as intent."

    override val daoMethods = 1

    override val safety = "Whole query verified by Room at compile time — RoomQL cannot do this."

    override val callSite = """
        @Query(
          "SELECT * FROM products " +
          "WHERE (:category IS NULL OR category = :category) " +
          "  AND (:minPrice IS NULL OR price >= :minPrice) " +
          "  AND (:minRating IS NULL OR rating >= :minRating) " +
          "  AND (:inStockOnly IS NULL OR in_stock = :inStockOnly) " +
          "ORDER BY rating DESC, id ASC"
        )
        fun search(
            category: String?, minPrice: Double?,
            minRating: Double?, inStockOnly: Boolean?,
        ): List<ProductEntity>
    """.trimIndent()

    override fun search(filters: SearchFilters): SearchResult {
        val products = dao.search(
            category = filters.category,
            minPrice = filters.minPrice,
            minRating = filters.minRating,
            inStockOnly = filters.inStockOnly,
        )
        return SearchResult(products = products, sql = SQL)
    }

    private companion object {
        /** Mirrors the @Query on IsNullOrProductDao.search, for the SQL readout. */
        const val SQL =
            "SELECT * FROM products\n" +
                "WHERE (? IS NULL OR category = ?)\n" +
                "  AND (? IS NULL OR price >= ?)\n" +
                "  AND (? IS NULL OR rating >= ?)\n" +
                "  AND (? IS NULL OR in_stock = ?)\n" +
                "ORDER BY rating DESC, id ASC"
    }
}
