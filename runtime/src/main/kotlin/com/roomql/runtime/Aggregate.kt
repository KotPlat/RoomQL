package com.roomql.runtime

/** Renders as `sqlFunction(operand)`, or `sqlFunction(*)` when [operand] is null (only [countAll] does this). */
internal data class AggregateExpression<T>(val sqlFunction: String, val operand: Expression<*>?) : Expression<T>

/** `COUNT(column)` — under a `LEFT JOIN`, an unmatched row counts as 0 here, unlike [countAll]. */
public fun count(column: Expression<*>): Expression<Long> = AggregateExpression("COUNT", column)

/** `COUNT(*)` — under a `LEFT JOIN`, an unmatched row counts as 1 here, unlike [count]. */
public fun countAll(): Expression<Long> = AggregateExpression("COUNT", null)

/** `SUM(column)` — `null` if no rows match, per SQL's own `SUM` behaviour. */
public fun <T : Number?> sum(column: Expression<T>): Expression<T?> = AggregateExpression("SUM", column)

/** Always [Double], regardless of [column]'s numeric type — matches SQL's own `AVG` behaviour. */
public fun <T : Number?> avg(column: Expression<T>): Expression<Double?> = AggregateExpression("AVG", column)

/** `MIN(column)` — the smallest non-null value, or `null` if no rows match. */
public fun <T> min(column: Expression<T>): Expression<T?> = AggregateExpression("MIN", column)

/** `MAX(column)` — the largest non-null value, or `null` if no rows match. */
public fun <T> max(column: Expression<T>): Expression<T?> = AggregateExpression("MAX", column)
