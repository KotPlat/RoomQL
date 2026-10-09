package com.roomql.sample

import android.database.sqlite.SQLiteException
import android.os.Build
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roomql.android.toQuery
import com.roomql.runtime.RoomQlException
import com.roomql.runtime.query
import com.roomql.sample.data.AppDatabase
import com.roomql.sample.data.CatalogueSeed
import com.roomql.sample.data.ProductEntityTable
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The 999 bind-argument cap only exists on the SQLite bundled with API 30 and below; run on such a device. */
@RunWith(AndroidJUnit4::class)
class BindLimitDeviceTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        assumeTrue("needs API 30 or below, got ${Build.VERSION.SDK_INT}", Build.VERSION.SDK_INT <= Build.VERSION_CODES.R)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        CatalogueSeed.populate(db)
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
    }

    @Test
    fun theCapIsReal_1000BoundValuesFailOnThisDevice() {
        val sql = "SELECT * FROM products WHERE name IN (${List(1000) { "?" }.joinToString(",")})"
        assertThrows(SQLiteException::class.java) {
            db.productDao().search(SimpleSQLiteQuery(sql, Array<Any?>(1000) { "n$it" }))
        }
    }

    @Test
    fun integerInListOf5000Runs() {
        val all = db.productDao().search(query { from(ProductEntityTable) }.toQuery())
        val ids = all.map { it.id } + (100_000 until 105_000)
        val rows = db.productDao().search(query { from(ProductEntityTable); where { ProductEntityTable.id inList ids } }.toQuery())
        assertEquals(all.size, rows.size)
    }

    @Test
    fun stringInListOf999Runs() {
        val names = List(999) { "missing-$it" }
        assertEquals(0, db.productDao().search(query { from(ProductEntityTable); where { ProductEntityTable.name inList names } }.toQuery()).size)
    }

    @Test
    fun stringInListOf1000FailsInBuildNotOnDevice() {
        assertThrows(RoomQlException::class.java) {
            query { from(ProductEntityTable); where { ProductEntityTable.name inList List(1000) { "n$it" } } }
        }
    }
}
