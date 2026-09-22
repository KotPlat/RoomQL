package com.roomql.demo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.roomql.demo.data.AppDatabase
import com.roomql.demo.data.CatalogueRepository
import com.roomql.demo.data.CatalogueSeed
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Validates the demo's only screen combining where { }, groupBy(...), and select(...) with a JOIN. */
@RunWith(RobolectricTestRunner::class)
class BrandSummaryTest {

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
    fun `unfiltered summary covers every brand and accounts for every product`() {
        val result = repository.brandSummary(inStockOnly = false)
        val brandNames = CatalogueSeed.brands().map { it.name }.toSet()

        assertEquals(brandNames, result.rows.map { it.brandName }.toSet())
        assertEquals(CatalogueSeed.PRODUCT_COUNT, result.rows.sumOf { it.productCount })
    }

    @Test
    fun `in-stock toggle narrows counts and averages to match a manual computation per brand`() {
        val brandNameById = CatalogueSeed.brands().associate { it.id to it.name }
        val expected = CatalogueSeed.products()
            .filter { it.inStock }
            .groupBy { brandNameById.getValue(it.brandId) }
            .mapValues { (_, rows) -> rows.size to rows.map { it.price }.average() }

        val result = repository.brandSummary(inStockOnly = true)

        assertEquals(expected.keys, result.rows.map { it.brandName }.toSet())
        for (row in result.rows) {
            val (expectedCount, expectedAvg) = expected.getValue(row.brandName)
            assertEquals(expectedCount, row.productCount)
            assertEquals(expectedAvg, row.avgPrice, 0.01)
        }
    }

    @Test
    fun `SQL composes where groupBy and select in one statement`() {
        val sql = repository.brandSummary(inStockOnly = true).sql
        assertTrue(sql.contains("WHERE"))
        assertTrue(sql.contains("GROUP BY"))
        assertTrue(sql.contains("COUNT("))
        assertTrue(sql.contains("AVG("))
        assertTrue(sql.contains("AS brand_name"))
    }
}
