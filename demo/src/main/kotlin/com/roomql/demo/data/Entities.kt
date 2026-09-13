package com.roomql.demo.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The demo catalogue.
 *
 * Note that [BrandEntity] deliberately carries `id` and `name`, both of which collide
 * with [ProductEntity]'s. That collision is the point, not an oversight: it is what
 * makes the faceted screen exercise RoomQL's automatic `table__column` aliasing, where
 * a joined query emits `products.id AS products__id` so a cursor cannot silently
 * overwrite one column with another. Renaming them apart would delete the feature this
 * demo exists to show.
 */
@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val category: String,
    @ColumnInfo(name = "brand_id") val brandId: Int,
    val price: Double,
    val rating: Double,
    @ColumnInfo(name = "in_stock") val inStock: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "brands")
data class BrandEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val country: String,
)

/**
 * Result shape for the faceted screen's JOIN.
 *
 * `category` and `price` are unique across the two tables, so RoomQL leaves them bare.
 * `id` and `name` collide, so they arrive aliased and must be mapped by their aliased
 * names — get this wrong and the field silently fails to map.
 */
data class ProductWithBrand(
    @ColumnInfo(name = "products__name") val productName: String,
    @ColumnInfo(name = "brands__name") val brandName: String,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "price") val price: Double,
)
