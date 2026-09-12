package com.roomql.runtime

@RoomQlDsl
class QueryBuilder {
    private var fromTable: String? = null
    private var fromEntityTable: EntityTable? = null
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

    fun from(table: EntityTable) {
        fromEntityTable = table
        fromTable = table.tableName
    }

    fun join(table: EntityTable, type: JoinType, block: JoinScope.() -> Unit) {
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
        roomQlCheck(joins.isEmpty() || fromEntityTable != null) {
            "join() requires from(EntityTable) so columns can be aliased; from(String) has no column metadata"
        }

        val collidingNames = collidingColumnNames()
        val args = mutableListOf<Any?>()
        val sql = buildString {
            if (joins.isNotEmpty() && fromEntityTable != null) {
                appendSelectWithAliasing(fromEntityTable!!, joins, collidingNames)
            } else {
                append("SELECT *")
            }
            append(" FROM $table")

            for (join in joins) {
                append(" ${join.type.keyword} JOIN ${join.table.tableName}")
                if (join.onCondition !is Condition.Empty) {
                    append(" ON ")
                    renderCondition(join.onCondition, args, this, collidingNames)
                }
            }

            val whereCondition = whereScope?.build() ?: Condition.Empty
            if (whereCondition !is Condition.Empty) {
                append(" WHERE ")
                renderCondition(whereCondition, args, this, collidingNames)
            }

            if (groupByColumn != null) {
                append(" GROUP BY ${groupByColumn!!.render(collidingNames)}")
                val havingCondition = havingScope?.build() ?: Condition.Empty
                if (havingCondition !is Condition.Empty) {
                    append(" HAVING ")
                    renderCondition(havingCondition, args, this, collidingNames)
                }
            }

            if (orderByClauses.isNotEmpty()) {
                append(" ORDER BY ")
                append(orderByClauses.joinToString(", ") { (col, dir) -> "${col.render(collidingNames)} $dir" })
            }

            limitValue?.let { append(" LIMIT $it") }
            offsetValue?.let { append(" OFFSET $it") }
        }

        return RoomQlQuery(sql, args)
    }

    /** Column names shared by more than one table in this query's FROM + JOINs. Empty when there are no joins. */
    private fun collidingColumnNames(): Set<String> {
        val primary = fromEntityTable ?: return emptySet()
        if (joins.isEmpty()) return emptySet()
        val allTables = listOf(primary) + joins.map { it.table }
        return allTables.flatMap { it.allColumnNames }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
    }
}

private const val SQL_AND = " AND "
private const val SQL_OR = " OR "

/** Qualifies with the table name only when the column name collides across the joined tables. */
private fun Column<*>.render(collidingNames: Set<String>): String =
    if (columnName in collidingNames) "$tableName.$columnName" else columnName

private fun renderCondition(condition: Condition, args: MutableList<Any?>, sb: StringBuilder, collidingNames: Set<String>) {
    when (condition) {
        is Condition.Empty -> Unit
        is Condition.Simple -> {
            args.addAll(condition.args)
            sb.append(condition.template.replace("%s", condition.column.render(collidingNames)))
        }
        is Condition.ColumnCompare ->
            sb.append("${condition.left.tableName}.${condition.left.columnName} = ${condition.right.tableName}.${condition.right.columnName}")
        is Condition.And -> sb.appendConditions(condition.conditions, SQL_AND, args, collidingNames)
        is Condition.Or -> {
            sb.append('(')
            sb.appendConditions(condition.conditions, SQL_OR, args, collidingNames)
            sb.append(')')
        }
    }
}

private fun StringBuilder.appendConditions(
    conditions: List<Condition>,
    separator: String,
    args: MutableList<Any?>,
    collidingNames: Set<String>,
) = conditions
    .filter { it !is Condition.Empty }
    .forEachIndexed { i, c ->
        if (i > 0) append(separator)
        renderCondition(c, args, this, collidingNames)
    }

fun query(block: QueryBuilder.() -> Unit): RoomQlQuery = QueryBuilder().apply(block).build()

private fun StringBuilder.appendSelectWithAliasing(primary: EntityTable, joins: List<JoinClause>, collidingNames: Set<String>) {
    val allTables = listOf(primary) + joins.map { it.table }
    append("SELECT ")
    var first = true
    for (table in allTables) {
        for (col in table.allColumnNames) {
            if (!first) append(", ")
            first = false
            if (col in collidingNames) {
                append("${table.tableName}.$col AS ${table.tableName}__$col")
            } else {
                append(col)
            }
        }
    }
}
