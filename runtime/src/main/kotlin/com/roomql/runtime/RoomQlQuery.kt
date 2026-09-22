package com.roomql.runtime

/**
 * The DSL's output: a rendered SQL string and its positional arguments, in binding order. A
 * plain-JVM value with no Android dependency, so you can assert on [sql] and [args] in an
 * ordinary unit test with no emulator or database connection. Pass it to Room via the
 * `toQuery()` extension in `:runtime-android` to get the `SupportSQLiteQuery` a `@RawQuery`
 * method accepts.
 */
public data class RoomQlQuery(
    /** The rendered SQL, with every value replaced by a positional `?` placeholder. */
    val sql: String,
    /** The bound values, in the same order their `?` placeholders appear in [sql]. */
    val args: List<Any?> = emptyList(),
)
