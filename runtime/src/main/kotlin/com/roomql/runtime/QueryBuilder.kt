package com.roomql.runtime

/**
 * Accumulates a query's shape through its `from`/`join`/`where`/`groupBy`/`having`/`select`/
 * `orderBy`/`limit`/`offset` calls, then renders it into a [RoomQlQuery] on [build]. This is
 * the receiver inside [query]'s block; construct it directly only if you need to call [build]
 * yourself instead of going through [query].
 *
 * Every setter here mutates this builder's own state and returns `Unit` rather than a new
 * instance — there is no immutable/fluent variant. **Not thread-safe**: build a query on one
 * thread or coroutine, and never share a half-built builder across threads. Marked
 * [RoomQlDsl] so nested blocks (`where { }`, `join { on { } }`, ...) cannot implicitly call
 * back out to these members.
 */
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

    /**
     * Sets the primary table by its raw SQL name. Carries no column metadata, so [build]
     * throws if this query also has a [join] — RoomQL cannot detect or alias colliding column
     * names without it. Only meant for a trusted, hard-coded table name; never pass an
     * untrusted string here, since nothing checks it against your schema.
     */
    public fun from(tableName: String) {
        fromTable = tableName
    }

    /**
     * Sets the primary table from a generated `*Table` object. Required if this query also has
     * a [join], since [EntityTable.allColumnNames] is what lets RoomQL detect and alias
     * colliding column names across the joined tables.
     */
    public fun from(table: EntityTable) {
        fromEntityTable = table
        fromTable = table.tableName
    }

    /**
     * Adds a `JOIN` against [table] of the given [type], with the `ON` predicate declared
     * inside [block]. Call repeatedly to join more than two tables. Requires [from] to have
     * been called with a generated `*Table` (not the raw-string overload) — [build] throws
     * [RoomQlException] otherwise, since joining needs column metadata to alias collisions.
     */
    public fun join(table: EntityTable, type: JoinType, block: JoinScope.() -> Unit) {
        val scope = JoinScope().apply(block)
        joins.add(JoinClause(table, type, scope.onCondition))
    }

    /**
     * Opens a [WhereScope] and applies [block] to it. Calling `where` more than once on the
     * same builder merges every block's conditions into one AND-combined set — each call
     * *adds* conditions, it never replaces the previous ones — which is what lets you build up
     * a `WHERE` clause from ordinary Kotlin control flow across several calls.
     */
    public fun where(block: WhereScope.() -> Unit) {
        val scope = whereScope ?: WhereScope().also { whereScope = it }
        scope.apply(block)
    }

    /**
     * Appends a sort key. Call repeatedly for a multi-column `ORDER BY`, rendered in call
     * order. Accepts any [Expression] — a bare [Column] or an aggregate — since SQL allows
     * both in `ORDER BY`.
     */
    public fun orderBy(expression: Expression<*>, direction: SortDirection) {
        orderByClauses.add(expression to direction)
    }

    /** Sets `LIMIT`. Repeated calls overwrite the previous value. [build] throws unless [n] is positive. */
    public fun limit(n: Int) {
        limitValue = n
    }

    /**
     * Sets `OFFSET`. Repeated calls overwrite the previous value. [build] throws unless [limit]
     * has also been set — a SQLite restriction on bare `OFFSET`, not a RoomQL one.
     */
    public fun offset(n: Int) {
        offsetValue = n
    }

    /**
     * Adds a column to `GROUP BY`. Call repeatedly for a multi-column `GROUP BY`, rendered in
     * call order. Takes [Column] only, never an aggregate — `GROUP BY COUNT(x)` is meaningless
     * SQL.
     */
    public fun groupBy(column: Column<*>) {
        groupByColumns.add(column)
    }

    /**
     * Opens a [HavingScope] and applies [block] to it, the same merging behaviour as [where].
     * [build] throws [RoomQlException] unless [groupBy] has also been called at least once.
     */
    public fun having(block: HavingScope.() -> Unit) {
        val scope = havingScope ?: HavingScope().also { havingScope = it }
        scope.apply(block)
    }

    /**
     * Projects specific columns and aggregates instead of whole rows. Left out entirely,
     * [build] renders `SELECT *` with automatic `JOIN`-collision aliasing; calling this even
     * once switches that off — [items] becomes the complete, explicit set of returned
     * columns. Repeated calls accumulate rather than replace, so the arguments across every
     * call form one projection list.
     *
     * [build] rejects shapes that would otherwise map the wrong value silently: a grouped
     * projection with a bare [Column] missing from [groupBy], an ungrouped projection mixing an
     * aggregate with a bare column, and two items sharing one output name.
     */
    public fun select(vararg items: SelectItem<*>) {
        selectItems.addAll(items)
    }

    /**
     * Validates the configured query and renders it into a [RoomQlQuery]. [query] calls this
     * for you; call it directly only if you built this [QueryBuilder] yourself. Throws
     * [RoomQlException] for every invalid configuration documented there. Safe to call more
     * than once — it re-renders the builder's current state each time rather than consuming it.
     */
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
