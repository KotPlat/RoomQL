package com.roomql.runtime

class QueryBuilder {
    private var fromTable: String? = null
    private var whereScope: ConditionScope? = null
    private val orderByClauses = mutableListOf<Pair<Column<*>, SortDirection>>()
    private var limitValue: Int? = null
    private var offsetValue: Int? = null
    private var groupByColumn: Column<*>? = null
    private var havingScope: ConditionScope? = null

    fun from(tableName: String) {
        fromTable = tableName
    }

    fun where(block: ConditionScope.() -> Unit) {
        val scope = whereScope ?: ConditionScope().also { whereScope = it }
        scope.apply(block)
    }

    fun orderBy(column: Column<*>, direction: SortDirection) {
        orderByClauses.add(column to direction)
    }

    fun limit(n: Int) {
        limitValue = n
    }

    fun offset(n: Int) {
        offsetValue = n
    }

    fun groupBy(column: Column<*>) {
        groupByColumn = column
    }

    fun having(block: ConditionScope.() -> Unit) {
        val scope = havingScope ?: ConditionScope().also { havingScope = it }
        scope.apply(block)
    }

    fun build(): RoomQlQuery {
        val table = fromTable ?: throw RoomQlException("from() must be called before build()")

        roomQlCheck(limitValue == null || limitValue!! > 0) { "limit() must be a positive integer, got $limitValue" }
        roomQlCheck(offsetValue == null || limitValue != null) { "offset() requires limit() to be set" }
        roomQlCheck(havingScope == null || groupByColumn != null) { "having() requires groupBy() to be set" }

        val args = mutableListOf<Any?>()
        val sql = buildString {
            append("SELECT * FROM $table")

            val whereCondition = whereScope?.build() ?: Condition.Empty
            if (whereCondition !is Condition.Empty) {
                append(" WHERE ")
                append(renderCondition(whereCondition, args))
            }

            if (groupByColumn != null) {
                append(" GROUP BY ${groupByColumn!!.columnName}")
                val havingCondition = havingScope?.build() ?: Condition.Empty
                if (havingCondition !is Condition.Empty) {
                    append(" HAVING ")
                    append(renderCondition(havingCondition, args))
                }
            }

            if (orderByClauses.isNotEmpty()) {
                append(" ORDER BY ")
                append(orderByClauses.joinToString(", ") { (col, dir) -> "${col.columnName} $dir" })
            }

            limitValue?.let { append(" LIMIT $it") }
            offsetValue?.let { append(" OFFSET $it") }
        }

        return RoomQlQuery(sql, args.toTypedArray())
    }
}

private fun renderCondition(condition: Condition, args: MutableList<Any?>): String = when (condition) {
    is Condition.Empty -> ""
    is Condition.Simple -> {
        args.addAll(condition.args)
        condition.sql
    }
    is Condition.And -> condition.conditions
        .filter { it !is Condition.Empty }
        .joinToString(" AND ") { renderCondition(it, args) }
    is Condition.Or -> condition.conditions
        .filter { it !is Condition.Empty }
        .joinToString(" OR ") { renderCondition(it, args) }
        .let { "($it)" }
}

fun query(block: QueryBuilder.() -> Unit): RoomQlQuery = QueryBuilder().apply(block).build()
