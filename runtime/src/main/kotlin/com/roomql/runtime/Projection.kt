package com.roomql.runtime

/** Renders as ``expression AS `alias` ``. Not an [Expression], so only [QueryBuilder.select] accepts it. */
internal data class AliasedExpression<T>(val alias: String, val expression: Expression<T>) : SelectItem<T>

/** Names an expression's output column — the escape hatch for a multi-column `select(...)` with no `@Projection`. */
public infix fun <T> Expression<T>.alias(name: String): SelectItem<T> = AliasedExpression(name, this)

/**
 * Marks a result data class whose constructor properties describe a multi-column `select(...)`
 * projection. RoomQL's KSP processor generates a factory function — `<ClassName>Projection` —
 * taking one `Expression<T>` per property, in declaration order, and returning the aliased
 * select items to spread into `select(...)`.
 *
 * A missing or mismatched-type argument is then an ordinary Kotlin compile error at the
 * generated function's call site, the same mechanism as forgetting a constructor argument.
 *
 * Source retention only: the processor reads the annotation at build time, and nothing about
 * it survives into the compiled class — consistent with RoomQL never reflecting at runtime.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
public annotation class Projection
