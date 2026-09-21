package com.roomql.runtime

public data class Column<T>(val columnName: String, val tableName: String) : Expression<T>
