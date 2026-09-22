package com.roomql.runtime

/**
 * Thrown only by [QueryBuilder.build] (and so [query]) for an invalid configuration; [message] names
 * the mistake. A half-built [QueryBuilder] never throws.
 */
public class RoomQlException(message: String) : RuntimeException(message)

internal inline fun roomQlCheck(value: Boolean, lazyMessage: () -> String) {
    if (!value) throw RoomQlException(lazyMessage())
}
