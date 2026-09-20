package com.roomql.runtime

internal sealed class Condition {
    object Empty : Condition()

    /** [template] contains exactly one `%s` placeholder for the rendered expression. */
    data class Simple(val expression: Expression<*>, val template: String, val args: List<Any?>) : Condition()

    /** Always renders both sides fully qualified — used for JOIN ON predicates. */
    data class ColumnCompare(val left: Column<*>, val right: Column<*>) : Condition()

    data class And(val conditions: List<Condition>) : Condition()
    data class Or(val conditions: List<Condition>) : Condition()
}

/** Collapses a scope's accumulated conditions into a single AND-combined [Condition]. */
internal fun List<Condition>.toCondition(): Condition = when {
    isEmpty() -> Condition.Empty
    size == 1 -> first()
    else -> Condition.And(this)
}
