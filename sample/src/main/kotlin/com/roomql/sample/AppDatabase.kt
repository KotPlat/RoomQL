package com.roomql.sample

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.RawQuery
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert
    fun insertAll(vararg users: UserEntity)

    /** One-shot query built by the caller via `query { }`. */
    @RawQuery
    fun search(query: SupportSQLiteQuery): List<UserEntity>

    /** Suspend variant. */
    @RawQuery
    suspend fun searchSuspend(query: SupportSQLiteQuery): List<UserEntity>

    /** Reactive variant; `observedEntities` must be declared manually. */
    @RawQuery(observedEntities = [UserEntity::class])
    fun observe(query: SupportSQLiteQuery): Flow<List<UserEntity>>
}

@Dao
interface OrderDao {
    @Insert
    fun insertAll(vararg orders: OrderEntity)

    /** JOIN: result mapped into the caller-supplied [UserOrder] POJO. */
    @RawQuery
    fun usersWithOrders(query: SupportSQLiteQuery): List<UserOrder>
}

@Database(
    entities = [UserEntity::class, OrderEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun orderDao(): OrderDao
}
