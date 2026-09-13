package com.roomql.demo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.roomql.demo.data.AppDatabase
import com.roomql.demo.data.CatalogueSeed
import com.roomql.demo.data.ProductEntity
import com.roomql.demo.search.ConcatSearch
import com.roomql.demo.search.IsNullOrSearch
import com.roomql.demo.search.OverloadedSearch
import com.roomql.demo.search.RoomQlSearch
import com.roomql.demo.search.SearchFilters
import com.roomql.demo.search.SearchStrategy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The test that validates the comparison rather than the library.
 *
 * The flagship screen claims four very different implementations return the same rows.
 * If one of the baselines were subtly wrong -- an inverted null check, a missing clause,
 * an argument bound out of order -- the demo would still *look* fine while quietly
 * misrepresenting the alternatives. Nothing else in the suite would catch that, so this
 * runs every strategy across every filter combination and requires exact agreement.
 */
@RunWith(RobolectricTestRunner::class)
class FlagshipSearchTest {

    private lateinit var db: AppDatabase
    private lateinit var strategies: List<SearchStrategy>

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        CatalogueSeed.populate(db)

        strategies = listOf(
            RoomQlSearch(db.productDao()),
            IsNullOrSearch(db.isNullOrProductDao()),
            OverloadedSearch(db.overloadedProductDao()),
            ConcatSearch(db.productDao()),
        )
    }

    @After
    fun tearDown() = db.close()

    /** All 16 combinations of the four filters, from none set to all four. */
    private fun allFilterCombinations(): List<SearchFilters> {
        val categories = listOf(null, "Laptops")
        val prices = listOf(null, 500.0)
        val ratings = listOf(null, 4.0)
        val stock = listOf(null, true)
        return categories.flatMap { c ->
            prices.flatMap { p ->
                ratings.flatMap { r ->
                    stock.map { s -> SearchFilters(c, p, r, s) }
                }
            }
        }
    }

    @Test
    fun `all four strategies agree on every filter combination`() {
        val combinations = allFilterCombinations()
        assertEquals("expected 2^4 filter combinations", 16, combinations.size)

        val reference = strategies.first()
        for (filters in combinations) {
            val expected = reference.search(filters).products.map(ProductEntity::id)
            for (strategy in strategies.drop(1)) {
                val actual = strategy.search(filters).products.map(ProductEntity::id)
                assertEquals(
                    "${strategy.label} disagreed with ${reference.label} for $filters",
                    expected,
                    actual,
                )
            }
        }
    }

    @Test
    fun `filters actually narrow the result set`() {
        // Guards against the degenerate pass where every strategy ignores its filters
        // and agreement is trivially true.
        for (strategy in strategies) {
            val unfiltered = strategy.search(SearchFilters()).products
            val filtered = strategy.search(
                SearchFilters(category = "Laptops", minPrice = 500.0),
            ).products

            assertEquals("${strategy.label} should see every row", CatalogueSeed.PRODUCT_COUNT, unfiltered.size)
            assertTrue("${strategy.label} did not narrow", filtered.size < unfiltered.size)
            assertTrue(filtered.isNotEmpty())
            assertTrue(filtered.all { it.category == "Laptops" && it.price >= 500.0 })
        }
    }

    @Test
    fun `every strategy reports the SQL it ran`() {
        val filters = SearchFilters(category = "Laptops", minRating = 4.0)
        for (strategy in strategies) {
            val sql = strategy.search(filters).sql
            assertTrue("${strategy.label} reported no SQL", sql.contains("SELECT"))
            assertTrue("${strategy.label} reported no table", sql.contains("products"))
        }
    }

    @Test
    fun `RoomQL emits minimal SQL while IS NULL OR always carries every clause`() {
        val roomQl = strategies.first { it.label == "RoomQL" }
        val isNullOr = strategies.first { it.label == "IS NULL OR" }
        val oneFilter = SearchFilters(category = "Laptops")

        // The concrete difference the demo claims: RoomQL's statement shrinks with the
        // filters, the IS NULL OR statement does not.
        val roomQlSql = roomQl.search(oneFilter).sql
        assertTrue(roomQlSql.contains("category = ?"))
        assertTrue("RoomQL should not mention unused columns", !roomQlSql.contains("rating >="))

        val isNullOrSql = isNullOr.search(oneFilter).sql
        assertTrue("IS NULL OR always ships every clause", isNullOrSql.contains("rating >="))
    }
}
