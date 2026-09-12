package com.roomql.runtime

/**
 * Confines implicit receiver access to the innermost DSL scope, so a nested block like
 * `where { }` cannot silently call outer [QueryBuilder] members such as `limit()` or `from()`.
 */
@DslMarker
internal annotation class RoomQlDsl
