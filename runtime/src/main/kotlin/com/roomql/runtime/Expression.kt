package com.roomql.runtime

/** Root type for anything that can appear in `having { }`, `orderBy`, or `select`: [Column]s and aggregates. */
public sealed interface Expression<T>
