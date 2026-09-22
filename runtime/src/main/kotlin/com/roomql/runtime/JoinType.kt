package com.roomql.runtime

/** The `JOIN` kind for [QueryBuilder.join]. RoomQL supports no other join kinds. */
public enum class JoinType(internal val keyword: String) {
    /** `INNER JOIN` — drops rows with no match on the other side. */
    INNER("INNER"),

    /** `LEFT JOIN` — keeps every primary-table row, with `NULL` for unmatched joined columns. */
    LEFT("LEFT"),
}
