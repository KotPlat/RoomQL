package com.roomql.android

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.roomql.runtime.RoomQlQuery

/**
 * Adapts a [RoomQlQuery] built by the pure-JVM `query { }` DSL into the
 * [SupportSQLiteQuery] that Room's `@RawQuery` methods accept.
 *
 * ```
 * dao.search(query { from(UserEntityTable); where { ... } }.toQuery())
 * ```
 *
 * This bridge lives in the Android-only `:runtime-android` module because
 * [SupportSQLiteQuery] is not available on the plain-JVM `:runtime` classpath.
 */
public fun RoomQlQuery.toQuery(): SupportSQLiteQuery = SimpleSQLiteQuery(sql, args.toTypedArray())
