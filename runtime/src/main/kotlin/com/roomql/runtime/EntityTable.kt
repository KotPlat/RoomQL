package com.roomql.runtime

/**
 * Implemented by every generated `object <EntityName>Table`. Passing one to
 * [QueryBuilder.from] — rather than the raw-string overload — is what lets RoomQL detect
 * colliding column names across a [QueryBuilder.join] and render the `table__column` aliasing;
 * the raw-string overload carries none of this metadata, which is why joining after it throws
 * [RoomQlException].
 */
public interface EntityTable {
    /** The table's SQL name — from `@Entity(tableName = ...)`, or the entity class name. */
    public val tableName: String

    /** Every column's SQL name, in declaration order. Used to detect JOIN name collisions. */
    public val allColumnNames: List<String>
}
