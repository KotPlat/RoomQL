package com.roomql.sample.edge

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.roomql.android.toQuery
import com.roomql.runtime.JoinType
import com.roomql.runtime.RoomQlException
import com.roomql.runtime.alias
import com.roomql.runtime.count
import com.roomql.runtime.max
import com.roomql.runtime.query
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/** Runs the crash edge cases from #88–#95 against a real Room database. */
@RunWith(RobolectricTestRunner::class)
class EdgeCaseRoomTest {

    private lateinit var db: EdgeCaseDatabase
    private lateinit var dao: EdgeDao
    private val o = OrderEntityTable
    private val c = CustomerEntityTable
    private val token = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

    private val first = OrderEntity(1, "a", 10, Tier.PRO, token, 'A', Address("Main", "Cairo", Geo(30.0, 31.2)))
    private val second = OrderEntity(2, "b", 99, Tier.FREE, UUID.randomUUID(), 'B', Address("High", "Giza", Geo(29.9, 31.1)))

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), EdgeCaseDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.dao()
        dao.insertOrders(first, second)
        dao.insertCustomers(CustomerEntity(10, "Mona"))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `enum, UUID and Char filters match Room's stored form`() {
        assertEquals(listOf(first), dao.orders(query { from(o); where { o.tier eq Tier.PRO } }.toQuery()))
        assertEquals(listOf(first), dao.orders(query { from(o); where { o.token eq token } }.toQuery()))
        assertEquals(listOf(second), dao.orders(query { from(o); where { o.grade eq 'B' } }.toQuery()))
        assertEquals(listOf(first, second), dao.orders(query { from(o); where { o.tier inList Tier.entries } }.toQuery()))
    }

    @Test
    fun `reserved-word table and column names run`() {
        val rows = dao.orders(query { from(o); where { o.group eq "b" }; orderBy(o.group, com.roomql.runtime.SortDirection.ASC) }.toQuery())
        assertEquals(listOf(second), rows)
    }

    @Test
    fun `embedded columns filter by their prefixed names`() {
        assertEquals(listOf(first), dao.orders(query { from(o); where { o.address.city eq "Cairo" } }.toQuery()))
        assertEquals(listOf(second), dao.orders(query { from(o); where { o.address.geo.lat lt 30.0 } }.toQuery()))
    }

    @Test
    fun `default join select runs with embedded and reserved-word columns`() {
        val rows = dao.orders(query { from(o); join(c, JoinType.INNER) { on { o.customerId eq c.id } } }.toQuery())
        assertEquals(1, rows.size)
        assertEquals(first.address, rows.single().address)
    }

    @Test
    fun `a large integer inList is inlined and runs`() {
        val ids = (1L..5000L).toList()
        assertEquals(2, dao.orders(query { from(o); where { o.id inList ids } }.toQuery()).size)
    }

    @Test
    fun `limit zero returns no rows`() {
        assertEquals(emptyList<OrderEntity>(), dao.orders(query { from(o); limit(0) }.toQuery()))
    }

    @Test
    fun `aliased aggregates map into a result class`() {
        val rows = dao.groupCounts(
            query { from(o); groupBy(o.group); select(o.group, count(o.id) alias "count"); orderBy(o.group, com.roomql.runtime.SortDirection.ASC) }.toQuery(),
        )
        assertEquals(listOf(GroupCount("a", 1), GroupCount("b", 1)), rows)
    }

    @Test
    fun `an unmatched LEFT JOIN maps into a nullable field`() {
        val rows = dao.orderCustomers(
            query {
                from(o)
                join(c, JoinType.LEFT) { on { o.customerId eq c.id } }
                select(o.id alias "orderId", c.name alias "name")
                orderBy(o.id, com.roomql.runtime.SortDirection.ASC)
            }.toQuery(),
        )
        assertEquals(listOf(OrderCustomer(1, "Mona"), OrderCustomer(2, null)), rows)
    }

    @Test
    fun `max over no rows is null in a nullable return type`() {
        assertNull(dao.singleLongOrNull(query { from(o); where { o.id gt 100L }; select(max(o.id)) }.toQuery()))
    }

    @Test
    fun `contains matches percent and underscore literally`() {
        fun order(id: Long, group: String) = first.copy(id = id, group = group)
        dao.insertOrders(order(10, "50% off"), order(11, "500 off"), order(12, "a_b"), order(13, "axb"), order(14, """c\d"""))
        fun groups(text: String) = dao.orders(query { from(o); where { o.group contains text } }.toQuery()).map { it.group }

        assertEquals(listOf("50% off"), groups("50%"))
        assertEquals(listOf("a_b"), groups("a_b"))
        assertEquals(listOf("""c\d"""), groups("""c\d"""))
    }

    @Test
    fun `self-join is rejected before it reaches SQLite`() {
        val e = runCatching { query { from(o); join(o, JoinType.LEFT) { on { o.id eq o.customerId } } } }.exceptionOrNull()
        assertTrue(e is RoomQlException)
    }
}
