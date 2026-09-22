package com.roomql.runtime

/** Renders as ``expression AS `alias` ``. Not an [Expression], so only [QueryBuilder.select] accepts it. */
internal data class AliasedExpression<T>(val alias: String, val expression: Expression<T>) : SelectItem<T>

/** Names an expression's output column — the escape hatch for a multi-column `select(...)` with no `@Projection`. */
public infix fun <T> Expression<T>.alias(name: String): SelectItem<T> = AliasedExpression(name, this)

/** Marks a result data class; KSP generates `<ClassName>Projection(...)`, one `Expression<T>` per property, for `select(...)`. */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
public annotation class Projection
