package com.roomql.runtime

/**
 * Common base for [WhereScope] and [HavingScope]: accumulates the [Condition]s built by their
 * infix operators, then collapses them into one AND-combined [Condition] via [build].
 *
 * [WhereScope] and [HavingScope] duplicate the same operator set on different receiver bounds
 * ([Column] vs [Expression]) because Kotlin can't express "whatever bound this scope has" as a
 * shared type parameter.
 *
 * Every value-taking operator comes in two forms, and which one you call is how you say
 * whether the filter is mandatory or optional:
 *
 * ```kotlin
 * where {
 *     UserTable.status eq "active"             // always applied; a nullable value will not compile
 *     UserTable.age gteIfNotNull minAge        // applied only when minAge is present
 *     UserTable.deletedAt.isNull()             // matches SQL NULL
 * }
 * ```
 *
 * The suffixed form is the only one that can disappear from the generated SQL, so reading
 * a `where { }` block tells you which conditions are optional without tracing the
 * nullability of every value at the call site.
 *
 * Note that an absent optional filter is *removed*, not matched against `NULL`. Every SQL
 * comparison involving `NULL` is never true — `age >= NULL` and even `age <> NULL` match
 * no rows at all — so a condition that cannot be satisfied is never what an absent filter
 * should mean. Use `isNull` or `isNotNull` when you want SQL `NULL` itself.
 *
 * The required operators take `T & Any` rather than `T`, so an ordinary nullable Kotlin
 * value fails to compile. That covers callers inside the type system; it does not cover a
 * `null` arriving through a Java caller or an unchecked cast, where generics erase and the
 * declared bound cannot stop it. Kotlin closes that gap itself: every public function with
 * a non-null parameter gets a `checkNotNullParameter` guard compiled into this artifact's
 * bytecode, so such a call still fails immediately with a `NullPointerException` rather
 * than silently binding `NULL` and matching no rows. Nothing in this file needs to guard
 * against it separately.
 */
@RoomQlDsl
public sealed class ConditionScope {
    internal val conditions = mutableListOf<Condition>()

    internal fun add(condition: Condition) {
        if (condition !is Condition.Empty) conditions.add(condition)
    }

    internal fun build(): Condition = conditions.toCondition()
}

/** The receiver inside `where { }` — accepts [Column]s only; aggregates are invalid SQL here. */
@RoomQlDsl
public class WhereScope : ConditionScope() {

    /**
     * Groups alternatives. The group is parenthesised and combined with the surrounding
     * conditions by `AND`. A group whose conditions all skip contributes nothing, so no
     * empty `()` is emitted.
     */
    public fun or(block: WhereScope.() -> Unit) {
        val scope = WhereScope().apply(block)
        if (scope.conditions.isNotEmpty()) add(Condition.Or(scope.conditions))
    }

    // ---- Comparisons: required ----

    public infix fun <T> Column<T>.eq(value: T & Any): Unit =
        add(Condition.Simple(this, "%s = ?", listOf(value)))

    public infix fun <T> Column<T>.notEq(value: T & Any): Unit =
        add(Condition.Simple(this, "%s != ?", listOf(value)))

    public infix fun <T> Column<T>.gt(value: T & Any): Unit =
        add(Condition.Simple(this, "%s > ?", listOf(value)))

    public infix fun <T> Column<T>.gte(value: T & Any): Unit =
        add(Condition.Simple(this, "%s >= ?", listOf(value)))

    public infix fun <T> Column<T>.lt(value: T & Any): Unit =
        add(Condition.Simple(this, "%s < ?", listOf(value)))

    public infix fun <T> Column<T>.lte(value: T & Any): Unit =
        add(Condition.Simple(this, "%s <= ?", listOf(value)))

    // ---- Comparisons: skipped when the value is absent ----

    public infix fun <T> Column<T>.eqIfNotNull(value: T?): Unit =
        skipIfNull(value) { this eq it }

    public infix fun <T> Column<T>.notEqIfNotNull(value: T?): Unit =
        skipIfNull(value) { this notEq it }

    public infix fun <T> Column<T>.gtIfNotNull(value: T?): Unit =
        skipIfNull(value) { this gt it }

    public infix fun <T> Column<T>.gteIfNotNull(value: T?): Unit =
        skipIfNull(value) { this gte it }

    public infix fun <T> Column<T>.ltIfNotNull(value: T?): Unit =
        skipIfNull(value) { this lt it }

    public infix fun <T> Column<T>.lteIfNotNull(value: T?): Unit =
        skipIfNull(value) { this lte it }

    // ---- Text matching ----
    //
    // The receiver bound keeps these off non-text columns. `like`/`notLike` take the SQL
    // pattern verbatim; `contains` wraps the value in `%` for you.

    public infix fun <T : String?> Column<T>.like(value: String): Unit =
        add(Condition.Simple(this, "%s LIKE ?", listOf(value)))

    public infix fun <T : String?> Column<T>.notLike(value: String): Unit =
        add(Condition.Simple(this, "%s NOT LIKE ?", listOf(value)))

    public infix fun <T : String?> Column<T>.contains(value: String): Unit =
        add(Condition.Simple(this, "%s LIKE ?", listOf("%$value%")))

    public infix fun <T : String?> Column<T>.likeIfNotNull(value: String?): Unit =
        skipIfNull(value) { this like it }

    public infix fun <T : String?> Column<T>.notLikeIfNotNull(value: String?): Unit =
        skipIfNull(value) { this notLike it }

    public infix fun <T : String?> Column<T>.containsIfNotNull(value: String?): Unit =
        skipIfNull(value) { this contains it }

    // ---- Null checks: these take no value and never skip ----

    public fun <T> Column<T>.isNull(): Unit =
        add(Condition.Simple(this, "%s IS NULL", emptyList()))

    public fun <T> Column<T>.isNotNull(): Unit =
        add(Condition.Simple(this, "%s IS NOT NULL", emptyList()))

    // ---- Set membership ----
    //
    // The optional forms are named for emptiness rather than nullability because an empty
    // list skips too: a multi-select with nothing selected means "no filter". The required
    // forms below translate faithfully to SQL instead of assuming that intent — see each
    // one's KDoc for what an empty list actually does.

    /**
     * An empty [values] renders `col IN ()`, which SQLite defines as matching no rows.
     * For "no filter" on an empty list instead, use [inListIfNotEmpty].
     */
    public infix fun <T> Column<T>.inList(values: List<T & Any>): Unit =
        add(Condition.Simple(this, "%s IN (${values.placeholders()})", values))

    /**
     * An empty [values] renders `col NOT IN ()`, which SQLite defines as matching every row —
     * the opposite of [inList]'s empty case. For "no filter" instead, use [notInListIfNotEmpty].
     */
    public infix fun <T> Column<T>.notInList(values: List<T & Any>): Unit =
        add(Condition.Simple(this, "%s NOT IN (${values.placeholders()})", values))

    public infix fun <T> Column<T>.inListIfNotEmpty(values: List<T & Any>?): Unit =
        skipIfEmpty(values) { this inList it }

    public infix fun <T> Column<T>.notInListIfNotEmpty(values: List<T & Any>?): Unit =
        skipIfEmpty(values) { this notInList it }

    // ---- Ranges ----

    /**
     * Both bounds are required, and there is deliberately no `betweenIfNotNull`: a range with
     * one bound missing is not a range, and skipping the whole condition would silently drop
     * the bound the caller did supply. Compose an open-ended range instead:
     *
     * ```kotlin
     * price gteIfNotNull minPrice
     * price lteIfNotNull maxPrice
     * ```
     *
     * which applies whichever bounds are present and has no hidden case.
     */
    public fun <T> Column<T>.between(lower: T & Any, upper: T & Any): Unit =
        add(Condition.Simple(this, "%s BETWEEN ? AND ?", listOf(lower, upper)))
}

/** The receiver inside `having { }` — accepts any [Expression], columns and aggregates alike. */
@RoomQlDsl
public class HavingScope : ConditionScope() {

    /** See [WhereScope.or] — same grouping behaviour, scoped to [HavingScope]. */
    public fun or(block: HavingScope.() -> Unit) {
        val scope = HavingScope().apply(block)
        if (scope.conditions.isNotEmpty()) add(Condition.Or(scope.conditions))
    }

    // ---- Comparisons: required ----

    public infix fun <T> Expression<T>.eq(value: T & Any): Unit =
        add(Condition.Simple(this, "%s = ?", listOf(value)))

    public infix fun <T> Expression<T>.notEq(value: T & Any): Unit =
        add(Condition.Simple(this, "%s != ?", listOf(value)))

    public infix fun <T> Expression<T>.gt(value: T & Any): Unit =
        add(Condition.Simple(this, "%s > ?", listOf(value)))

    public infix fun <T> Expression<T>.gte(value: T & Any): Unit =
        add(Condition.Simple(this, "%s >= ?", listOf(value)))

    public infix fun <T> Expression<T>.lt(value: T & Any): Unit =
        add(Condition.Simple(this, "%s < ?", listOf(value)))

    public infix fun <T> Expression<T>.lte(value: T & Any): Unit =
        add(Condition.Simple(this, "%s <= ?", listOf(value)))

    // ---- Comparisons: skipped when the value is absent ----

    public infix fun <T> Expression<T>.eqIfNotNull(value: T?): Unit =
        skipIfNull(value) { this eq it }

    public infix fun <T> Expression<T>.notEqIfNotNull(value: T?): Unit =
        skipIfNull(value) { this notEq it }

    public infix fun <T> Expression<T>.gtIfNotNull(value: T?): Unit =
        skipIfNull(value) { this gt it }

    public infix fun <T> Expression<T>.gteIfNotNull(value: T?): Unit =
        skipIfNull(value) { this gte it }

    public infix fun <T> Expression<T>.ltIfNotNull(value: T?): Unit =
        skipIfNull(value) { this lt it }

    public infix fun <T> Expression<T>.lteIfNotNull(value: T?): Unit =
        skipIfNull(value) { this lte it }

    // ---- Text matching ----

    public infix fun <T : String?> Expression<T>.like(value: String): Unit =
        add(Condition.Simple(this, "%s LIKE ?", listOf(value)))

    public infix fun <T : String?> Expression<T>.notLike(value: String): Unit =
        add(Condition.Simple(this, "%s NOT LIKE ?", listOf(value)))

    public infix fun <T : String?> Expression<T>.contains(value: String): Unit =
        add(Condition.Simple(this, "%s LIKE ?", listOf("%$value%")))

    public infix fun <T : String?> Expression<T>.likeIfNotNull(value: String?): Unit =
        skipIfNull(value) { this like it }

    public infix fun <T : String?> Expression<T>.notLikeIfNotNull(value: String?): Unit =
        skipIfNull(value) { this notLike it }

    public infix fun <T : String?> Expression<T>.containsIfNotNull(value: String?): Unit =
        skipIfNull(value) { this contains it }

    // ---- Null checks: these take no value and never skip ----

    public fun <T> Expression<T>.isNull(): Unit =
        add(Condition.Simple(this, "%s IS NULL", emptyList()))

    public fun <T> Expression<T>.isNotNull(): Unit =
        add(Condition.Simple(this, "%s IS NOT NULL", emptyList()))

    // ---- Set membership ----

    /** See [WhereScope.inList] for what an empty [values] renders. */
    public infix fun <T> Expression<T>.inList(values: List<T & Any>): Unit =
        add(Condition.Simple(this, "%s IN (${values.placeholders()})", values))

    /** See [WhereScope.notInList] for what an empty [values] renders. */
    public infix fun <T> Expression<T>.notInList(values: List<T & Any>): Unit =
        add(Condition.Simple(this, "%s NOT IN (${values.placeholders()})", values))

    public infix fun <T> Expression<T>.inListIfNotEmpty(values: List<T & Any>?): Unit =
        skipIfEmpty(values) { this inList it }

    public infix fun <T> Expression<T>.notInListIfNotEmpty(values: List<T & Any>?): Unit =
        skipIfEmpty(values) { this notInList it }

    // ---- Ranges ----

    /** See [WhereScope.between] for why there is deliberately no `betweenIfNotNull`. */
    public fun <T> Expression<T>.between(lower: T & Any, upper: T & Any): Unit =
        add(Condition.Simple(this, "%s BETWEEN ? AND ?", listOf(lower, upper)))
}

private fun List<*>.placeholders(): String = joinToString(",") { "?" }

private inline fun <T : Any> skipIfNull(value: T?, emit: (T) -> Unit) {
    if (value != null) emit(value)
}

private inline fun <T : Any> skipIfEmpty(values: List<T>?, emit: (List<T>) -> Unit) {
    if (!values.isNullOrEmpty()) emit(values)
}
