package com.roomql.demo.data

import com.roomql.android.toQuery
import com.roomql.runtime.Column
import com.roomql.runtime.JoinType
import com.roomql.runtime.SortDirection
import com.roomql.runtime.query
import kotlinx.coroutines.flow.Flow

/** Rows plus the SQL that produced them, so every screen can show its own statement. */
data class QueryResult<T>(val rows: List<T>, val sql: String)

/** A reactive query and the SQL behind it. */
data class ObservedQuery<T>(val flow: Flow<List<T>>, val sql: String)

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
     * The faceted screen's query: multi-select categories joined to brands.
     *
     * Both conditions are independently optional. An empty chip selection drops the
     * IN clause entirely rather than matching nothing, which is the behaviour naive
     * implementations usually get backwards.
     */
    fun facets(categories: List<String>?, country: String?): QueryResult<ProductWithBrand> {
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
        return QueryResult(productDao.searchWithBrand(q.toQuery()), q.sql)
    }

    /** Sorting and paging chosen at runtime — not expressible as a static `@Query`. */
    fun page(
        sortColumn: SortColumn,
        direction: SortDirection,
        limit: Int,
        offset: Int,
    ): QueryResult<ProductEntity> {
        val q = query {
            from(ProductEntityTable)
            orderBy(sortColumn.column, direction)
            limit(limit)
            // OFFSET requires LIMIT in SQLite; RoomQL rejects the combination at build().
            offset(offset)
        }
        return QueryResult(productDao.search(q.toQuery()), q.sql)
    }

    /** Reactive search. Re-emits because ProductDao.observe declares observedEntities. */
    fun observeByName(fragment: String?): ObservedQuery<ProductEntity> {
        val q = query {
            from(ProductEntityTable)
            where { ProductEntityTable.name contains fragment }
            orderBy(ProductEntityTable.name, SortDirection.ASC)
        }
        return ObservedQuery(productDao.observe(q.toQuery()), q.sql)
    }
}

/** The columns the sortable screen offers, so the UI cannot name a column that does not exist. */
enum class SortColumn(val label: String, internal val column: Column<*>) {
    Name("Name", ProductEntityTable.name),
    Price("Price", ProductEntityTable.price),
    Rating("Rating", ProductEntityTable.rating),
}
