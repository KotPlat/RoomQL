package com.roomql.demo.data

import androidx.room.Dao
import androidx.room.Query

/**
 * Baseline 1: one `@Query` carrying the `(:x IS NULL OR col = :x)` trick per filter.
 *
 * This is the most common workaround in real Android codebases. It is a single method,
 * which is its appeal -- but read the SQL: the intent is buried, every filter costs a
 * duplicated parameter reference, and SQLite must evaluate the null check for every row
 * of every query. It also cannot express a dynamic ORDER BY at all, because a column
 * name is not bindable as a parameter.
 */
@Dao
interface IsNullOrProductDao {

    @Query(
        """
        SELECT * FROM products
        WHERE (:category IS NULL OR category = :category)
          AND (:minPrice IS NULL OR price >= :minPrice)
          AND (:minRating IS NULL OR rating >= :minRating)
          AND (:inStockOnly IS NULL OR in_stock = :inStockOnly)
        ORDER BY rating DESC, id ASC
        """,
    )
    fun search(
        category: String?,
        minPrice: Double?,
        minRating: Double?,
        inStockOnly: Boolean?,
    ): List<ProductEntity>
}
