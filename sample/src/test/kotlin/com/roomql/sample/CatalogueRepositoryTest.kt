package com.roomql.sample

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.roomql.android.toQuery
import com.roomql.runtime.query
import com.roomql.sample.data.AppDatabase
import com.roomql.sample.data.CatalogueRepository
import com.roomql.sample.data.CatalogueSeed
import com.roomql.sample.data.ProductEntity
import com.roomql.sample.data.ProductEntityTable
import com.roomql.sample.data.SortColumn
import com.roomql.runtime.SortDirection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * End-to-end coverage of the catalogue queries against a real in-memory Room database:
 * generated *Table objects -> query { } -> .toQuery() -> Room.
 */
@RunWith(RobolectricTestRunner::class)
class CatalogueRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: CatalogueRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        CatalogueSeed.populate(db)
        repository = CatalogueRepository(db.productDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `seed data is deterministic`() {
        assertEquals(CatalogueSeed.PRODUCT_COUNT, repository.search().size)
        assertEquals(CatalogueSeed.products(), CatalogueSeed.products())
    }

    @Test
    fun `all filters null returns everything`() {
        assertEquals(CatalogueSeed.PRODUCT_COUNT, repository.search().size)
    }

    @Test
    fun `null filter is omitted, not treated as a null match`() {
        val filtered = repository.search(category = "Laptops")
        assertTrue(filtered.isNotEmpty())
        assertTrue(filtered.all { it.category == "Laptops" })
        // Dropping the filter must widen the result set, not match NULL.
        assertTrue(repository.search().size > filtered.size)
    }

    @Test
    fun `every filter combination narrows correctly`() {
        val all = repository.search()
        val byPrice = repository.search(minPrice = 500.0)
        val byPriceAndRating = repository.search(minPrice = 500.0, minRating = 4.0)

        assertTrue(byPrice.all { it.price >= 500.0 })
        assertTrue(byPriceAndRating.all { it.price >= 500.0 && it.rating >= 4.0 })
        assertTrue(byPriceAndRating.size <= byPrice.size)
        assertTrue(byPrice.size <= all.size)
    }

    @Test
    fun `boolean filter binds correctly`() {
        val inStock = repository.search(inStockOnly = true)
        assertTrue(inStock.isNotEmpty())
        assertTrue(inStock.all(ProductEntity::inStock))
    }

    @Test
    fun `suspend variant runs`() = runBlocking {
        val cameras = repository.searchSuspend("Cameras")
        assertTrue(cameras.all { it.category == "Cameras" })
    }

    @Test
    fun `required inList on an empty list executes IN () against real SQLite`() {
        // inListIfNotEmpty's skip-on-empty behaviour is asserted above via byCategories.
        // The required inList takes a different path deliberately: it renders a literal
        // IN () / NOT IN () rather than skipping, and that SQL needs to actually run
        // against SQLite once, not just be asserted as a string in :runtime.
        val none = query {
            from(ProductEntityTable)
            where { ProductEntityTable.category inList emptyList() }
        }
        assertEquals(0, db.productDao().search(none.toQuery()).size)

        val all = query {
            from(ProductEntityTable)
            where { ProductEntityTable.category notInList emptyList() }
        }
        assertEquals(CatalogueSeed.PRODUCT_COUNT, db.productDao().search(all.toQuery()).size)
    }

    @Test
    fun `empty facet list means no filter, not match-nothing`() {
        assertEquals(CatalogueSeed.PRODUCT_COUNT, repository.byCategories(emptyList()).size)
        assertEquals(CatalogueSeed.PRODUCT_COUNT, repository.byCategories(null).size)

        val twoCategories = repository.byCategories(listOf("Laptops", "Cameras"))
        assertTrue(twoCategories.isNotEmpty())
        assertTrue(twoCategories.all { it.category == "Laptops" || it.category == "Cameras" })
    }

    @Test
    fun `join maps colliding columns into the result class`() {
        val joined = repository.withBrand(category = "Monitors")
        assertTrue(joined.isNotEmpty())
        // Both names survive the join: proof the table__column aliasing mapped correctly
        // rather than one column silently overwriting the other.
        assertTrue(joined.all { it.productName.isNotBlank() && it.brandName.isNotBlank() })
        assertTrue(joined.all { it.category == "Monitors" })
    }

    @Test
    fun `join filtered on a column from the joined table`() {
        val japanese = repository.withBrand(country = "Japan")
        assertTrue(japanese.isNotEmpty())
        assertTrue(japanese.all { it.brandName == "Cobalt" })
    }

    @Test
    fun `faceted query combines an empty-safe IN clause with a joined-table filter`() {
        // No chips selected and no country: the IN and the country condition both drop,
        // so the join returns the whole catalogue rather than nothing.
        assertEquals(CatalogueSeed.PRODUCT_COUNT, repository.facets(emptyList(), null).size)

        val japaneseLaptops = repository.facets(listOf("Laptops"), "Japan")
        assertTrue(japaneseLaptops.isNotEmpty())
        assertTrue(japaneseLaptops.all { it.category == "Laptops" })
        assertTrue(japaneseLaptops.all { it.brandName == "Cobalt" })
        // Both colliding columns mapped through their aliases.
        assertTrue(japaneseLaptops.all { it.productName.isNotBlank() })
    }

    @Test
    fun `paging and runtime sort direction`() {
        val firstPage = repository.page(SortColumn.Price, SortDirection.ASC, limit = 10, offset = 0)
        val secondPage = repository.page(SortColumn.Price, SortDirection.ASC, limit = 10, offset = 10)
        val descending = repository.page(SortColumn.Price, SortDirection.DESC, limit = 10, offset = 0)

        assertEquals(10, firstPage.size)
        assertEquals(firstPage.map(ProductEntity::price).sorted(), firstPage.map(ProductEntity::price))
        assertTrue(firstPage.last().price <= secondPage.first().price)
        assertTrue(descending.first().price >= firstPage.last().price)
    }

    @Test
    fun `flow re-emits when an observed table changes`() = runBlocking {
        val flow = repository.observeByName("Pro")
        val before = flow.first()

        db.productDao().insertAll(
            listOf(
                ProductEntity(
                    id = 99_999,
                    name = "Pro Inserted 000",
                    category = "Laptops",
                    brandId = 1,
                    price = 1_000.0,
                    rating = 5.0,
                    inStock = true,
                    createdAt = 0L,
                ),
            ),
        )

        val after = flow.first()
        assertEquals(before.size + 1, after.size)
    }
}
