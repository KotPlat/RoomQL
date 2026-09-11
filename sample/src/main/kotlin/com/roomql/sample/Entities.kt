package com.roomql.sample

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val age: Int,
    val status: String,
)

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val id: Int,
    val userId: Int,
    val total: Double,
    val status: String,
)

/**
 * Result shape for the JOIN example. `name` and `total` are unique across the two
 * tables, so RoomQl leaves them un-aliased; `id`/`status` collide and are aliased to
 * `users__id`, `orders__id`, etc. (see [com.roomql.runtime.QueryBuilder]).
 */
data class UserOrder(
    @ColumnInfo(name = "name") val userName: String,
    @ColumnInfo(name = "total") val orderTotal: Double,
)
