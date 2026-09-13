package com.roomql.demo.search

import com.roomql.demo.data.ProductEntity

/** The four optional filters the flagship screen exposes. A null field means "not filtered". */
data class SearchFilters(
    val category: String? = null,
    val minPrice: Double? = null,
    val minRating: Double? = null,
    val inStockOnly: Boolean? = null,
) {
    val activeCount: Int
        get() = listOfNotNull(category, minPrice, minRating, inStockOnly).size
}

/** What a strategy returns: the rows, and the SQL it actually ran. */
data class SearchResult(
    val products: List<ProductEntity>,
    val sql: String,
)

/**
 * One way of implementing "search products by an arbitrary subset of four filters".
 *
 * Four implementations exist, and the demo switches between them at runtime. They must
 * all return the same rows for the same filters -- `FlagshipSearchTest` asserts exactly
 * that, because if a baseline were subtly wrong the whole comparison would be dishonest.
 */
interface SearchStrategy {
    /** Short label for the UI selector. Kept tight so it fits a chip. */
    val label: String

    /** One-line summary of the approach and its cost. */
    val summary: String

    /**
     * DAO methods this approach needs to support four optional filters.
     *
     * This is the number the 2^n argument is about, and showing it next to the code is
     * the point of the demo: the SQL alone never reveals what the approach costs you.
     */
    val daoMethods: Int

    /** The Kotlin you write at the call site — the actual cost of using this approach. */
    val callSite: String

    /** What the compiler checks for you, and what it does not. */
    val safety: String

    fun search(filters: SearchFilters): SearchResult
}
