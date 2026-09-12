package com.roomql.runtime

class JoinScope {
    internal var onCondition: Condition = Condition.Empty

    fun on(block: JoinConditionScope.() -> Unit) {
        onCondition = JoinConditionScope().apply(block).build()
    }
}

class JoinConditionScope {
    internal val conditions = mutableListOf<Condition>()

    infix fun <T> Column<T>.eq(other: Column<T>) {
        conditions.add(Condition.ColumnCompare(this, other))
    }

    internal fun build(): Condition = when {
        conditions.isEmpty() -> Condition.Empty
        conditions.size == 1 -> conditions.first()
        else -> Condition.And(conditions)
    }
}

internal data class JoinClause(
    val table: TableColumns,
    val type: JoinType,
    val onCondition: Condition,
)
