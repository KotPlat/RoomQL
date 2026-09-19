package com.example.data
import androidx.room.Entity

@Entity(tableName = "rooms")
data class RoomEntity(
    val id: Int
)
