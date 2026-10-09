package com.roomql.runtime

/** The receiver inside [query]: accumulates the query's clauses, then renders them on [build]. Not thread-safe. */
@RoomQlDsl
public class QueryBuilder {
    private var fromTable: String? = null
    private var fromEntityTable: EntityTable? = null
    private val joins = mutableListOf<JoinClause>()
    private var whereScope: WhereScope? = null
    private val orderByClauses = mutableListOf<Pair<Expression<*>, SortDirection>>()
    private var limitValue: Int? = null
    private var offsetValue: Int? = null
    private val groupByColumns = mutableListOf<Column<*>>()
    private var havingScope: HavingScope? = null
    private val selectItems = mutableListOf<SelectItem<*>>()

    /** Sets the primary table by name, quoted like every identifier; carries no column metadata, so [build] rejects a [join] after it. */
    public fun from(tableName: String) {
        fromTable = tableName
    }

    /** Sets the primary table from a generated `*Table`; required for [join], which needs its column names to alias collisions. */
    public fun from(table: EntityTable) {
        fromEntityTable = table
        fromTable = table.tableName
    }

    /** Adds a `JOIN` of [type] against [table], with its `ON` predicate in [block]. Requires [from] with a `*Table`. */
    public fun join(table: EntityTable, type: JoinType, block: JoinScope.() -> Unit) {
        val scope = JoinScope().apply(block)
        joins.add(JoinClause(table, type, scope.onCondition))
    }

    /** Opens a [WhereScope]; repeated calls add to one AND-combined set rather than replacing it. */
    public fun where(block: WhereScope.() -> Unit) {
        val scope = whereScope ?: WhereScope().also { whereScope = it }
        scope.apply(block)
    }

    /** Appends a sort key on a [Column] or aggregate; repeated calls render in call order. */
    public fun orderBy(expression: Expression<*>, direction: SortDirection) {
        orderByClauses.add(expression to direction)
    }

    /** Sets `LIMIT`; `0` returns no rows. Repeated calls overwrite the previous value. [build] throws if [n] is negative. */
    public fun limit(n: Int) {
        limitValue = n
    }

    /** Sets `OFFSET`, overwriting any previous value. [build] throws unless [limit] is also set (a SQLite rule). */
    public fun offset(n: Int) {
        offsetValue = n
    }

    /** Adds a [Column] to `GROUP BY`; repeated calls render in call order. Aggregates are not accepted. */
    public fun groupBy(column: Column<*>) {
        groupByColumns.add(column)
    }

    /** Opens a [HavingScope], merging like [where]. [build] throws unless [groupBy] is set. */
    public fun having(block: HavingScope.() -> Unit) {
        val scope = havingScope ?: HavingScope().also { havingScope = it }
        scope.apply(block)
    }

    /** Projects [items] instead of whole rows, switching off `JOIN`-collision aliasing; repeated calls accumulate. */
    public fun select(vararg items: SelectItem<*>) {
        selectItems.addAll(items)
    }

    /** Validates and renders this builder into a [RoomQlQuery], throwing [RoomQlException] if invalid. Safe to call repeatedly. */
    public fun build(): RoomQlQuery {
        val table = fromTable ?: throw RoomQlException("from() must be called before build()")
        val limit = limitValue
        val entityTable = fromEntityTable

        roomQlCheck(limit == null || limit >= 0) { "limit() must not be negative, got $limit" }
        roomQlCheck(offsetValue == null || limit != null) { "offset() requires limit() to be set" }
        roomQlCheck(havingScope == null || groupByColumns.isNotEmpty()) { "having() requires groupBy() to be set" }
        roomQlCheck(joins.isEmpty() || entityTable != null) {
            "join() requires from(EntityTable) so columns can be aliased; from(String) has no column metadata"
        }
        if (entityTable != null) checkTables(entityTable)
        val collidingNames = collidingColumnNames()
        checkSelectProjection(collidingNames)

        val args = mutableListOf<Any?>()
        val sql = buildString {
            if (selectItems.isNotEmpty()) {
                append("SELECT ")
                append(selectItems.joinToString(", ") { it.renderSelectItem(collidingNames) })
            } else if (joins.isNotEmpty() && entityTable != null) {
                appendSelectWithAliasing(entityTable, joins, collidingNames)
            } else {
                append("SELECT *")
            }
            append(" FROM ${quote(table)}")

            for (join in joins) {
                append(" ${join.type.keyword} JOIN ${quote(join.table.tableName)}")
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

            if (groupByColumns.isNotEmpty()) {
                append(" GROUP BY ")
                append(groupByColumns.joinToString(", ") { it.render(collidingNames) })
                val havingCondition = havingScope?.build() ?: Condition.Empty
                if (havingCondition !is Condition.Empty) {
                    append(" HAVING ")
                    renderCondition(havingCondition, args, this, collidingNames)
                }
            }

            if (orderByClauses.isNotEmpty()) {
                append(" ORDER BY ")
                append(orderByClauses.joinToString(", ") { (col, dir) -> "${col.render(collidingNames)} ${dir.keyword}" })
            }

            limit?.let { append(" LIMIT $it") }
            offsetValue?.let { append(" OFFSET $it") }
        }

        roomQlCheck(args.size <= MAX_BIND_ARGS) {
            "query binds ${args.size} values, but SQLite on Android API 30 and below allows at most $MAX_BIND_ARGS. " +
                "Integer inList() values are inlined and don't count; split other large lists across queries"
        }
        return RoomQlQuery(sql, args)
    }

    /** Rejects a self-join, which needs table aliases RoomQL doesn't have, and any column from a table outside from/join. */
    private fun checkTables(primary: EntityTable) {
        val tableNames = listOf(primary.tableName) + joins.map { it.table.tableName }
        val repeated = tableNames.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        roomQlCheck(repeated.isEmpty()) {
            "table ${repeated.joinToString()} appears more than once in from()/join(); self-joins aren't supported yet"
        }

        val referenced = selectItems.flatMap { it.parts().first.columns() } +
            joins.flatMap { it.onCondition.columns() } +
            (whereScope?.build()?.columns() ?: emptyList()) +
            groupByColumns +
            (havingScope?.build()?.columns() ?: emptyList()) +
            orderByClauses.flatMap { it.first.columns() }
        val foreign = referenced.filter { it.tableName !in tableNames }.distinct()
        roomQlCheck(foreign.isEmpty()) {
            "column(s) from a table not in from()/join(): ${foreign.joinToString { it.describe() }}"
        }
    }

    /** Rejects a select(...) that would let Room or SQLite map the wrong value silently. */
    private fun checkSelectProjection(collidingNames: Set<String>) {
        if (selectItems.isEmpty()) return
        val expressions = selectItems.map { it.parts().first }
        val bareColumns = expressions.filterIsInstance<Column<*>>()
        val hasAggregate = expressions.any { it is AggregateExpression<*> }
        if (groupByColumns.isNotEmpty()) {
            val ungrouped = bareColumns.filter { it !in groupByColumns }
            roomQlCheck(ungrouped.isEmpty()) {
                "select(...) column(s) not in groupBy(): ${ungrouped.joinToString { it.columnName }}"
            }
        } else {
            roomQlCheck(!(hasAggregate && bareColumns.isNotEmpty())) {
                "select(...) cannot mix an aggregate with a bare column unless groupBy() is set"
            }
        }

        if (selectItems.size > 1) {
            val unnamed = selectItems.filterIsInstance<AggregateExpression<*>>().map { it.describe() }
            roomQlCheck(unnamed.isEmpty()) {
                "select(...) with more than one item needs an alias on each aggregate, or Room can't map it: ${unnamed.joinToString()}"
            }
        }

        val badAliases = selectItems.filterIsInstance<AliasedExpression<*>>().map { it.alias }.filter { '`' in it }
        roomQlCheck(badAliases.isEmpty()) { "alias() names cannot contain a backtick: ${badAliases.joinToString()}" }
        val duplicates = selectItems.map { it.outputName(collidingNames) }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        roomQlCheck(duplicates.isEmpty()) { "select(...) returns more than one column named: ${duplicates.joinToString()}; alias all but one" }
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

/** Qualifies a column with its table name only when the column name collides across the joined tables. */
private fun Expression<*>.render(collidingNames: Set<String>): String = when (this) {
    is Column<*> -> if (columnName in collidingNames) "${quote(tableName)}.${quote(columnName)}" else quote(columnName)
    is AggregateExpression<*> -> "$sqlFunction(${operand?.render(collidingNames) ?: "*"})"
}

/** Splits an item into its expression and its alias, or null when unaliased. */
private fun SelectItem<*>.parts(): Pair<Expression<*>, String?> = when (this) {
    is Expression<*> -> this to null
    is AliasedExpression<*> -> expression to alias
}

// Room quotes identifiers too, so a reserved word (`order`, `group`) works as a name; SQLite reports it unquoted.
private fun quote(identifier: String): String {
    roomQlCheck('`' !in identifier) { "table and column names cannot contain a backtick: $identifier" }
    return "`$identifier`"
}

private fun Expression<*>.columns(): List<Column<*>> = when (this) {
    is Column<*> -> listOf(this)
    is AggregateExpression<*> -> operand?.columns() ?: emptyList()
}

private fun Condition.columns(): List<Column<*>> = when (this) {
    is Condition.Empty -> emptyList()
    is Condition.Simple -> expression.columns()
    is Condition.ColumnCompare -> listOf(left, right)
    is Condition.And -> conditions.flatMap { it.columns() }
    is Condition.Or -> conditions.flatMap { it.columns() }
}

private fun SelectItem<*>.renderSelectItem(collidingNames: Set<String>): String {
    val (expression, alias) = parts()
    val rendered = expression.render(collidingNames)
    return if (alias == null) rendered else "$rendered AS `$alias`"
}

/** The column name Room sees: the alias, a bare column's name, or an unaliased aggregate's own SQL text. */
private fun SelectItem<*>.outputName(collidingNames: Set<String>): String {
    val (expression, alias) = parts()
    return alias ?: if (expression is Column<*>) expression.columnName else expression.render(collidingNames)
}

private fun renderCondition(condition: Condition, args: MutableList<Any?>, sb: StringBuilder, collidingNames: Set<String>) {
    when (condition) {
        is Condition.Empty -> Unit
        is Condition.Simple -> {
            condition.args.mapTo(args) { toBindable(it, condition.expression) }
            sb.append(condition.template.replace("%s", condition.expression.render(collidingNames)))
        }
        is Condition.ColumnCompare ->
            sb.append("${condition.left.qualified()} = ${condition.right.qualified()}")
        is Condition.And -> sb.appendConditions(condition.conditions, SQL_AND, args, collidingNames)
        is Condition.Or -> {
            sb.append('(')
            sb.appendConditions(condition.conditions, SQL_OR, args, collidingNames)
            sb.append(')')
        }
    }
}

private fun Column<*>.qualified(): String = "${quote(tableName)}.${quote(columnName)}"

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

/**
 * RoomQL's entry point. Applies [block] to a fresh [QueryBuilder], calls
 * [QueryBuilder.build], and returns the finished [RoomQlQuery]. Throws [RoomQlException] if
 * the configured query is invalid.
 */
public fun query(block: QueryBuilder.() -> Unit): RoomQlQuery = QueryBuilder().apply(block).build()

private fun StringBuilder.appendSelectWithAliasing(primary: EntityTable, joins: List<JoinClause>, collidingNames: Set<String>) {
    val allTables = listOf(primary) + joins.map { it.table }
    append("SELECT ")
    var first = true
    for (table in allTables) {
        for (col in table.allColumnNames) {
            if (!first) append(", ")
            first = false
            if (col in collidingNames) {
                append("${quote(table.tableName)}.${quote(col)} AS ${quote("${table.tableName}__$col")}")
            } else {
                append(quote(col))
            }
        }
    }
}
