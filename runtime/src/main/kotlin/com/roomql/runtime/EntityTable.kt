package com.roomql.runtime

interface EntityTable {
    val tableName: String
    val allColumnNames: List<String>
}
