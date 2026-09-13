package com.roomql.demo.search

import com.roomql.android.toQuery
import com.roomql.runtime.SortDirection
import com.roomql.runtime.query
import com.roomql.demo.data.ProductDao
import com.roomql.demo.data.ProductEntityTable

/**
 * The RoomQL implementation: one query, no branching, no duplicated parameters.
 *
 * Each condition simply drops out of the generated SQL when its value is null, so this
 * one block covers all 16 filter combinations that [OverloadedSearch] needs 16 methods
 * for, and it produces the *minimal* SQL for each -- unlike [IsNullOrSearch], which
 * always ships every clause.
 */
class RoomQlSearch(private val dao: ProductDao) : SearchStrategy {

    override val label = "RoomQL"

    override val summary =
        "One query { } block. Null filters vanish from the SQL, so the statement is " +
            "always minimal and column names are checked by the compiler."

    override val daoMethods = 1

    override val safety = "Column names checked at compile time. Whole-query SQL is not."

    override val callSite = """
        query {
            from(ProductEntityTable)
            where {
                ProductEntityTable.category eq filters.category
                ProductEntityTable.price gte filters.minPrice
                ProductEntityTable.rating gte filters.minRating
                ProductEntityTable.inStock eq filters.inStockOnly
            }
            orderBy(ProductEntityTable.rating, SortDirection.DESC)
            orderBy(ProductEntityTable.id, SortDirection.ASC)
        }
    """.trimIndent()

    override fun search(filters: SearchFilters): SearchResult {
        val q = query {
            from(ProductEntityTable)
            where {
                ProductEntityTable.category eq filters.category
                ProductEntityTable.price gte filters.minPrice
                ProductEntityTable.rating gte filters.minRating
                ProductEntityTable.inStock eq filters.inStockOnly
            }
            orderBy(ProductEntityTable.rating, SortDirection.DESC)
            orderBy(ProductEntityTable.id, SortDirection.ASC)
        }
        return SearchResult(products = dao.search(q.toQuery()), sql = q.sql)
    }
}
