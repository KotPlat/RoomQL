package com.roomql.demo.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Insert
    fun insertAll(products: List<ProductEntity>)

    /** Row count, used only to decide whether the catalogue still needs seeding. */
    @Query("SELECT COUNT(*) FROM products")
    fun count(): Int

    /** One-shot query built by the caller with `query { }`. */
    @RawQuery
    fun search(query: SupportSQLiteQuery): List<ProductEntity>

    /**
     * Reactive variant. `observedEntities` must be listed by hand: Room cannot inspect a
     * raw query to learn which tables it reads, so omitting this produces a Flow that
     * emits once and then never again.
     */
    @RawQuery(observedEntities = [ProductEntity::class])
    fun observe(query: SupportSQLiteQuery): Flow<List<ProductEntity>>

    /** JOIN results, mapped into the caller-supplied [ProductWithBrand]. */
    @RawQuery
    fun searchWithBrand(query: SupportSQLiteQuery): List<ProductWithBrand>
}

@Dao
interface BrandDao {
    @Insert
    fun insertAll(brands: List<BrandEntity>)
}

@Database(
    entities = [ProductEntity::class, BrandEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun brandDao(): BrandDao

    // The two baseline DAOs the flagship screen compares RoomQL against.
    abstract fun isNullOrProductDao(): IsNullOrProductDao
    abstract fun overloadedProductDao(): OverloadedProductDao
}
