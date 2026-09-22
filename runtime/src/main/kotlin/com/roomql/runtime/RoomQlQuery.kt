package com.roomql.runtime

/** The DSL's output: SQL plus positional args, pure JVM so tests can assert on it; `toQuery()` hands it to Room. */
public data class RoomQlQuery(
    /** The rendered SQL, with every value replaced by a positional `?` placeholder. */
    val sql: String,
    /** The bound values, in the same order their `?` placeholders appear in [sql]. */
    val args: List<Any?> = emptyList(),
)
