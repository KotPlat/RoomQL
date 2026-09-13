package com.roomql.runtime

public data class RoomQlQuery(
    val sql: String,
    val args: List<Any?> = emptyList(),
)
