package com.roomql.runtime

class ConditionScope {
    internal val conditions = mutableListOf<Condition>()

    private fun add(condition: Condition) {
        if (condition !is Condition.Empty) conditions.add(condition)
    }

    fun or(block: ConditionScope.() -> Unit) {
        val scope = ConditionScope().apply(block)
        if (scope.conditions.isNotEmpty()) add(Condition.Or(scope.conditions))
    }

    internal fun build(): Condition = when {
        conditions.isEmpty() -> Condition.Empty
        conditions.size == 1 -> conditions.first()
        else -> Condition.And(conditions)
    }

    // ---- Operators as extension functions so they auto-register inside this scope ----

    infix fun <T> Column<T>.eq(value: T?) =
        add(if (value == null) Condition.Empty else Condition.Simple("$columnName = ?", listOf(value)))

    infix fun <T> Column<T>.notEq(value: T?) =
        add(if (value == null) Condition.Empty else Condition.Simple("$columnName != ?", listOf(value)))

    infix fun <T> Column<T>.gt(value: T?) =
        add(if (value == null) Condition.Empty else Condition.Simple("$columnName > ?", listOf(value)))

    infix fun <T> Column<T>.gte(value: T?) =
        add(if (value == null) Condition.Empty else Condition.Simple("$columnName >= ?", listOf(value)))

    infix fun <T> Column<T>.lt(value: T?) =
        add(if (value == null) Condition.Empty else Condition.Simple("$columnName < ?", listOf(value)))

    infix fun <T> Column<T>.lte(value: T?) =
        add(if (value == null) Condition.Empty else Condition.Simple("$columnName <= ?", listOf(value)))

    infix fun <T> Column<T>.like(value: String?) =
        add(if (value == null) Condition.Empty else Condition.Simple("$columnName LIKE ?", listOf(value)))

    infix fun <T> Column<T>.notLike(value: String?) =
        add(if (value == null) Condition.Empty else Condition.Simple("$columnName NOT LIKE ?", listOf(value)))

    infix fun <T> Column<T>.contains(value: String?) =
        add(if (value == null) Condition.Empty else Condition.Simple("$columnName LIKE ?", listOf("%$value%")))

    fun <T> Column<T>.isNull() = add(Condition.Simple("$columnName IS NULL", emptyList()))

    fun <T> Column<T>.isNotNull() = add(Condition.Simple("$columnName IS NOT NULL", emptyList()))

    infix fun <T> Column<T>.inList(values: List<T>?) {
        if (values.isNullOrEmpty()) return
        val placeholders = values.joinToString(",") { "?" }
        add(Condition.Simple("$columnName IN ($placeholders)", values))
    }

    infix fun <T> Column<T>.notInList(values: List<T>?) {
        if (values.isNullOrEmpty()) return
        val placeholders = values.joinToString(",") { "?" }
        add(Condition.Simple("$columnName NOT IN ($placeholders)", values))
    }

    fun <T> Column<T>.between(lower: T?, upper: T?) =
        add(
            if (lower == null || upper == null) Condition.Empty
            else Condition.Simple("$columnName BETWEEN ? AND ?", listOf(lower, upper))
        )
}
