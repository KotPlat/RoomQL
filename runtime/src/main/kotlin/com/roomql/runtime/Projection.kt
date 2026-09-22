package com.roomql.runtime

/** Renders as `expression AS alias`. Only meaningful inside `select(...)`. */
internal data class AliasedExpression<T>(val alias: String, val expression: Expression<T>) : Expression<T>

/** Names an expression's output column — the escape hatch for a multi-column `select(...)` with no `@RoomQlProjection`. */
public infix fun <T> Expression<T>.alias(name: String): Expression<T> = AliasedExpression(name, this)
