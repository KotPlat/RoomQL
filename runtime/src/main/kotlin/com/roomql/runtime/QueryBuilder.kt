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

    /** Sets the primary table by raw name; carries no column metadata, so [build] rejects a [join] after it. Trusted names only. */
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

    /** Sets `LIMIT`. Repeated calls overwrite the previous value. [build] throws unless [n] is positive. */
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

        roomQlCheck(limit == null || limit > 0) { "limit() must be a positive integer, got $limit" }
        roomQlCheck(offsetValue == null || limit != null) { "offset() requires limit() to be set" }
        roomQlCheck(havingScope == null || groupByColumns.isNotEmpty()) { "having() requires groupBy() to be set" }
        roomQlCheck(joins.isEmpty() || entityTable != null) {
            "join() requires from(EntityTable) so columns can be aliased; from(String) has no column metadata"
        }
        checkSelectProjection()

        val collidingNames = collidingColumnNames()
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

        return RoomQlQuery(sql, args)
    }

    /** Rejects a select(...) that would let Room or SQLite map the wrong value silently. */
    private fun checkSelectProjection() {
        if (selectItems.isEmpty()) return
        val expressions = selectItems.map { it.unaliased() }
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

        val badAliases = selectItems.filterIsInstance<AliasedExpression<*>>().map { it.alias }.filter { '`' in it }
        roomQlCheck(badAliases.isEmpty()) { "alias() names cannot contain a backtick: ${badAliases.joinToString()}" }
        val duplicates = selectItems.mapNotNull { it.outputName() }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
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
    is Column<*> -> if (columnName in collidingNames) "$tableName.$columnName" else columnName
    is AggregateExpression<*> -> "$sqlFunction(${operand?.render(collidingNames) ?: "*"})"
}

// Quoted so a reserved word (`order`, `group`) still works as an alias; SQLite reports the name unquoted.
private fun SelectItem<*>.renderSelectItem(collidingNames: Set<String>): String = when (this) {
    is Expression<*> -> render(collidingNames)
    is AliasedExpression<*> -> "${expression.render(collidingNames)} AS `$alias`"
}

private fun SelectItem<*>.unaliased(): Expression<*> = when (this) {
    is Expression<*> -> this
    is AliasedExpression<*> -> expression
}

/** The column name Room sees for this item, or null for an unaliased aggregate. */
private fun SelectItem<*>.outputName(): String? = when (this) {
    is Column<*> -> columnName
    is AggregateExpression<*> -> null
    is AliasedExpression<*> -> alias
}

private fun renderCondition(condition: Condition, args: MutableList<Any?>, sb: StringBuilder, collidingNames: Set<String>) {
    when (condition) {
        is Condition.Empty -> Unit
        is Condition.Simple -> {
            args.addAll(condition.args)
            sb.append(condition.template.replace("%s", condition.expression.render(collidingNames)))
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
                append("${table.tableName}.$col AS ${table.tableName}__$col")
            } else {
                append(col)
            }
        }
    }
}
