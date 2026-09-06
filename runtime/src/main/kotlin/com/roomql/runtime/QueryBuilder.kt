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
                renderCondition(whereCondition, args, this)
            }

            if (groupByColumn != null) {
                append(" GROUP BY ${groupByColumn!!.columnName}")
                val havingCondition = havingScope?.build() ?: Condition.Empty
                if (havingCondition !is Condition.Empty) {
                    append(" HAVING ")
                    renderCondition(havingCondition, args, this)
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

private const val SQL_AND = " AND "
private const val SQL_OR = " OR "

private fun renderCondition(condition: Condition, args: MutableList<Any?>, sb: StringBuilder) {
    when (condition) {
        is Condition.Empty -> Unit
        is Condition.Simple -> {
            args.addAll(condition.args)
            sb.append(condition.sql)
        }
        is Condition.And -> sb.appendConditions(condition.conditions, SQL_AND, args)
        is Condition.Or -> {
            sb.append('(')
            sb.appendConditions(condition.conditions, SQL_OR, args)
            sb.append(')')
        }
    }
}

private fun StringBuilder.appendConditions(
    conditions: List<Condition>,
    separator: String,
    args: MutableList<Any?>,
) = conditions
    .filter { it !is Condition.Empty }
    .forEachIndexed { i, c ->
        if (i > 0) append(separator)
        renderCondition(c, args, this)
    }

fun query(block: QueryBuilder.() -> Unit): RoomQlQuery = QueryBuilder().apply(block).build()
