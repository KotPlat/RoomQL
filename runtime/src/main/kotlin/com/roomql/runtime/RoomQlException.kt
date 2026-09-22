package com.roomql.runtime

/**
 * Thrown by [QueryBuilder.build] — and therefore by [query] — when the configured query is
 * invalid: a missing `from()`, a non-positive `limit()`, `offset()` without `limit()`,
 * `having()` without `groupBy()`, a `join()` on a raw-string `from(String)`, or a `select(...)`
 * projection `build()` rejects (see [QueryBuilder.select]). [message] names the specific
 * mistake.
 *
 * All validation is deferred to `build()`, so a half-built [QueryBuilder] never throws while
 * you are still configuring it. These are programming errors rather than user-input errors —
 * a query that is wrong is wrong on every run — which is why [RoomQlException] is unchecked.
 */
public class RoomQlException(message: String) : RuntimeException(message)

internal inline fun roomQlCheck(value: Boolean, lazyMessage: () -> String) {
    if (!value) throw RoomQlException(lazyMessage())
}
