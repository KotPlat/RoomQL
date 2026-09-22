package com.roomql.runtime

/** Implemented by every generated `object <EntityName>Table`; the column metadata [QueryBuilder.join] needs. */
public interface EntityTable {
    /** The table's SQL name — from `@Entity(tableName = ...)`, or the entity class name. */
    public val tableName: String

    /** Every column's SQL name, in declaration order. Used to detect JOIN name collisions. */
    public val allColumnNames: List<String>
}
