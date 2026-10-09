package com.roomql.sample.edge

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.RawQuery
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import java.util.UUID

enum class Tier { FREE, PRO }

data class Geo(val lat: Double, val lng: Double)

data class Address(val street: String, @ColumnInfo(name = "city_name") val city: String, @Embedded(prefix = "geo_") val geo: Geo)

// Reserved-word table and column names on purpose.
@Entity(tableName = "order")
data class OrderEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "group") val group: String,
    @ColumnInfo(name = "customer_id") val customerId: Long,
    val tier: Tier,
    val token: UUID,
    val grade: Char,
    @Embedded(prefix = "addr_") val address: Address,
) {
    val label: String get() = "#$id"
}

@Entity(tableName = "customers")
data class CustomerEntity(@PrimaryKey val id: Long, val name: String)

data class GroupCount(val group: String, val count: Long)

// A LEFT-joined column can come back NULL, so its field must be nullable.
data class OrderCustomer(val orderId: Long, val name: String?)

@Dao
interface EdgeDao {
    @Insert fun insertOrders(vararg orders: OrderEntity)
    @Insert fun insertCustomers(vararg customers: CustomerEntity)
    @RawQuery fun orders(query: SupportSQLiteQuery): List<OrderEntity>
    @RawQuery fun groupCounts(query: SupportSQLiteQuery): List<GroupCount>
    @RawQuery fun orderCustomers(query: SupportSQLiteQuery): List<OrderCustomer>
    @RawQuery fun singleLong(query: SupportSQLiteQuery): Long
    @RawQuery fun singleLongOrNull(query: SupportSQLiteQuery): Long?
}

@Database(entities = [OrderEntity::class, CustomerEntity::class], version = 1, exportSchema = false)
abstract class EdgeCaseDatabase : RoomDatabase() {
    abstract fun dao(): EdgeDao
}
