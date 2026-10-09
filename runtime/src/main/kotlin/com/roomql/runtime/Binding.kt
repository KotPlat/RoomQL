package com.roomql.runtime

import java.nio.ByteBuffer
import java.util.UUID

/** SQLite's bind-argument cap on Android API 30 and below (SQLite < 3.32). */
internal const val MAX_BIND_ARGS = 999

/** Converts [value] into the form Room itself stores, or throws for a type only a `@TypeConverter` can store. */
internal fun toBindable(value: Any?, expression: Expression<*>): Any? = when (value) {
    null, is String, is Long, is Int, is Short, is Byte, is Double, is Float, is Boolean, is ByteArray -> value
    is Enum<*> -> value.name
    is Char -> value.code
    is UUID -> ByteBuffer.allocate(16).putLong(value.mostSignificantBits).putLong(value.leastSignificantBits).array()
    else -> throw RoomQlException(
        "can't bind a ${value::class.qualifiedName} to ${expression.describe()}: Room stores it through a " +
            "@TypeConverter, which RoomQL can't apply. Filter on the stored form instead, e.g. Column<Long>(columnName, tableName)",
    )
}

/** True when every value is an integer type, which is safe to inline into the SQL instead of binding. */
internal fun List<*>.isAllIntegers(): Boolean = all { it is Int || it is Long || it is Short || it is Byte }

/** Unquoted, table-qualified form for error messages. */
internal fun Expression<*>.describe(): String = when (this) {
    is Column<*> -> "$tableName.$columnName"
    is AggregateExpression<*> -> "$sqlFunction(${operand?.describe() ?: "*"})"
}
