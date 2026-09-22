package com.roomql.runtime

/**
 * A typed column reference, generated per property on each `*Table` object. `T` keeps the property's
 * nullability, so the [WhereScope]/[HavingScope] operators type-check against it.
 */
public data class Column<T>(val columnName: String, val tableName: String) : Expression<T>
