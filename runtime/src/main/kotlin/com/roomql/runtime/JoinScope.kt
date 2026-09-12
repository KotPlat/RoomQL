package com.roomql.runtime

@RoomQlDsl
public class JoinScope {
    internal var onCondition: Condition = Condition.Empty

    public fun on(block: JoinConditionScope.() -> Unit) {
        onCondition = JoinConditionScope().apply(block).build()
    }
}

@RoomQlDsl
public class JoinConditionScope {
    internal val conditions = mutableListOf<Condition>()

    public infix fun <T> Column<T>.eq(other: Column<T>) {
        conditions.add(Condition.ColumnCompare(this, other))
    }

    internal fun build(): Condition = conditions.toCondition()
}

internal data class JoinClause(
    val table: EntityTable,
    val type: JoinType,
    val onCondition: Condition,
)
