package com.roomql.runtime

data class RoomQlQuery(
    val sql: String,
    val args: List<Any?> = emptyList(),
)
