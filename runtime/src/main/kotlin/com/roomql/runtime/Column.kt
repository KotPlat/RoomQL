package com.roomql.runtime

/**
 * A typed reference to one SQL column. `T` carries the referenced property's Kotlin type,
 * including nullability, so the condition operators in [WhereScope]/[HavingScope] type-check
 * against it.
 *
 * You do not construct these by hand — RoomQL's KSP processor generates one `Column<T>` per
 * property on every `object <EntityName>Table`, alongside [columnName] and [tableName] taken
 * straight from the entity's `@ColumnInfo`/`@Entity(tableName = ...)` (or the Kotlin property
 * and class names, when neither is present).
 */
public data class Column<T>(val columnName: String, val tableName: String) : Expression<T>
