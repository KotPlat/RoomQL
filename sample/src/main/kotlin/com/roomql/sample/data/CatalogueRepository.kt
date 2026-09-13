package com.roomql.sample.data

import com.roomql.android.toQuery
import com.roomql.runtime.Column
import com.roomql.runtime.JoinType
import com.roomql.runtime.SortDirection
import com.roomql.runtime.query
import kotlinx.coroutines.flow.Flow

/**
 * Every catalogue query the demo screens run, built with RoomQL.
 *
 * Query construction deliberately lives here rather than in the ViewModels: this is the
 * layer people copy, and `query { }` belongs next to the data source, not in the UI.
 */
class CatalogueRepository(
    private val productDao: ProductDao,
) {

    /**
     * The flagship query. Every parameter is optional, and a `null` one is *absent from
     * the SQL* rather than matched against NULL — which is the whole point: one function
     * covers all 16 combinations of four filters.
     */
    fun search(
        category: String? = null,
        minPrice: Double? = null,
        minRating: Double? = null,
        inStockOnly: Boolean? = null,
    ): List<ProductEntity> {
        val q = query {
            from(ProductEntityTable)
            where {
                ProductEntityTable.category eq category
                ProductEntityTable.price gte minPrice
                ProductEntityTable.rating gte minRating
                ProductEntityTable.inStock eq inStockOnly
            }
            orderBy(ProductEntityTable.rating, SortDirection.DESC)
        }
        return productDao.search(q.toQuery())
    }

    suspend fun searchSuspend(category: String?): List<ProductEntity> {
        val q = query {
            from(ProductEntityTable)
            where { ProductEntityTable.category eq category }
        }
        return productDao.searchSuspend(q.toQuery())
    }

    /**
     * Faceted selection. An empty or null list drops the condition entirely, so "no chips
     * selected" means "no filter" rather than "match nothing".
     */
    fun byCategories(categories: List<String>?): List<ProductEntity> {
        val q = query {
            from(ProductEntityTable)
            where { ProductEntityTable.category inList categories }
        }
        return productDao.search(q.toQuery())
    }

    /**
     * Joined query. `products.id`/`products.name` collide with `brands.id`/`brands.name`,
     * so RoomQL aliases them to `products__id`, `brands__name`, and so on — which is why
     * [ProductWithBrand] maps those aliased names.
     */
    fun withBrand(category: String? = null, country: String? = null): List<ProductWithBrand> {
        val q = query {
            from(ProductEntityTable)
            join(BrandEntityTable, JoinType.INNER) {
                on { ProductEntityTable.brandId eq BrandEntityTable.id }
            }
            where {
                ProductEntityTable.category eq category
                BrandEntityTable.country eq country
            }
        }
        return productDao.searchWithBrand(q.toQuery())
    }

    /**
     * The faceted screen's query: multi-select categories joined to brands.
     *
     * Both conditions are independently optional. An empty chip selection drops the
     * IN clause entirely rather than matching nothing, which is the behaviour naive
     * implementations usually get backwards.
     */
    fun facets(categories: List<String>?, country: String?): List<ProductWithBrand> {
        val q = query {
            from(ProductEntityTable)
            join(BrandEntityTable, JoinType.INNER) {
                on { ProductEntityTable.brandId eq BrandEntityTable.id }
            }
            where {
                ProductEntityTable.category inList categories
                BrandEntityTable.country eq country
            }
            orderBy(ProductEntityTable.name, SortDirection.ASC)
        }
        return productDao.searchWithBrand(q.toQuery())
    }

    /** Sorting and paging chosen at runtime — not expressible as a static `@Query`. */
    fun page(
        sortColumn: SortColumn,
        direction: SortDirection,
        limit: Int,
        offset: Int,
    ): List<ProductEntity> {
        val q = query {
            from(ProductEntityTable)
            orderBy(sortColumn.column, direction)
            limit(limit)
            // OFFSET requires LIMIT in SQLite; RoomQL rejects the combination at build().
            offset(offset)
        }
        return productDao.search(q.toQuery())
    }

    /** Reactive search. Re-emits because ProductDao.observe declares observedEntities. */
    fun observeByName(fragment: String?): Flow<List<ProductEntity>> {
        val q = query {
            from(ProductEntityTable)
            where { ProductEntityTable.name contains fragment }
            orderBy(ProductEntityTable.name, SortDirection.ASC)
        }
        return productDao.observe(q.toQuery())
    }
}

/** The columns the sortable screen offers, so the UI cannot name a column that does not exist. */
enum class SortColumn(val label: String, internal val column: Column<*>) {
    Name("Name", ProductEntityTable.name),
    Price("Price", ProductEntityTable.price),
    Rating("Rating", ProductEntityTable.rating),
}
