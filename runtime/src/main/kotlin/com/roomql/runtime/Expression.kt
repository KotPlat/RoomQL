package com.roomql.runtime

/** Anything [QueryBuilder.select] accepts: a bare [Expression], or one named with [alias]. */
public sealed interface SelectItem<out T>

/** Root type for anything that can appear in `having { }`, `orderBy`, or `select`: [Column]s and aggregates. */
public sealed interface Expression<T> : SelectItem<T>
