package com.roomql.runtime

/** The `JOIN` kind for [QueryBuilder.join]. RoomQL supports no other join kinds. */
public enum class JoinType(internal val keyword: String) {
    /** `INNER JOIN` — drops rows with no match on the other side. */
    INNER("INNER"),

    /**
     * `LEFT JOIN` — keeps every row from the primary table, with `NULL` for unmatched columns
     * on the joined side. The one join kind an always-false `ON` can meaningfully gate away,
     * since `INNER JOIN` with a false predicate drops every row instead of neutralizing itself.
     */
    LEFT("LEFT"),
}
