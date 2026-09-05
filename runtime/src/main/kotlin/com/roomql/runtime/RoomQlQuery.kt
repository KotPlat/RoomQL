package com.roomql.runtime

data class RoomQlQuery(
    val sql: String,
    val args: Array<Any?> = emptyArray(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RoomQlQuery) return false
        return sql == other.sql && args.contentEquals(other.args)
    }

    override fun hashCode(): Int = 31 * sql.hashCode() + args.contentHashCode()
}
