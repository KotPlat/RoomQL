package com.roomql.runtime

class QueryBuilder {
    private var fromTable: String? = null
    private var fromTableColumns: TableColumns? = null
    private val joins = mutableListOf<JoinClause>()
    private var whereScope: ConditionScope? = null
    private val orderByClauses = mutableListOf<Pair<Column<*>, SortDirection>>()
    private var limitValue: Int? = null
    private var offsetValue: Int? = null
    private var groupByColumn: Column<*>? = null
    private var havingScope: ConditionScope? = null

    fun from(tableName: String) {
        fromTable = tableName
    }

    fun from(table: TableColumns) {
        fromTableColumns = table
        fromTable = table.tableName
    }

    fun join(table: TableColumns, type: JoinType, block: JoinScope.() -> Unit) {
        val scope = JoinScope().apply(block)
        joins.add(JoinClause(table, type, scope.onCondition))
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
            if (joins.isNotEmpty() && fromTableColumns != null) {
                appendSelectWithAliasing(fromTableColumns!!, joins)
            } else {
                append("SELECT *")
            }
            append(" FROM $table")

            for (join in joins) {
                append(" ${join.type.keyword} JOIN ${join.table.tableName}")
                if (join.onCondition !is Condition.Empty) {
                    append(" ON ")
                    renderCondition(join.onCondition, args, this)
                }
            }

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

private fun StringBuilder.appendSelectWithAliasing(primary: TableColumns, joins: List<JoinClause>) {
    val allTables = listOf(primary) + joins.map { it.table }
    val nameCount = allTables.flatMap { it.allColumnNames }.groupBy { it }.mapValues { it.value.size }
    append("SELECT ")
    var first = true
    for (table in allTables) {
        for (col in table.allColumnNames) {
            if (!first) append(", ")
            first = false
            if ((nameCount[col] ?: 0) > 1) {
                append("${table.tableName}.$col AS ${table.tableName}__$col")
            } else {
                append(col)
            }
        }
    }
}
