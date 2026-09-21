# RoomQL API Reference

Every public type, function, and operator in **RoomQL** — the type-safe Kotlin DSL for building dynamic Android Room queries — with the SQL each one generates. For worked examples see the [Usage Guide](USAGE.md); for installation and the overall pitch see the [main README](../README.md).

The public surface is deliberately small and is frozen by [Binary Compatibility Validator](https://github.com/Kotlin/binary-compatibility-validator), so everything RoomQL exposes is on this page.

---

## Contents

- [`com.roomql.runtime` — the DSL](#comroomqlruntime--the-dsl)
  - [query](#query)
  - [QueryBuilder](#querybuilder)
  - [WhereScope and HavingScope: the condition operators](#wherescope-and-havingscope-the-condition-operators)
  - [JoinScope and JoinConditionScope](#joinscope-and-joinconditionscope)
  - [Column](#column)
  - [Aggregate functions](#aggregate-functions)
  - [EntityTable](#entitytable)
  - [RoomQlQuery](#roomqlquery)
  - [JoinType and SortDirection](#jointype-and-sortdirection)
  - [RoomQlException](#roomqlexception)
- [`com.roomql.android` — the Room bridge](#comroomqlandroid--the-room-bridge)
- [`com.roomql.ksp` — the code generator](#comroomqlksp--the-code-generator)
- [Generated code shape](#generated-code-shape)

---

## `com.roomql.runtime` — the DSL

Artifact `io.github.kotplat.roomql:runtime`. Pure JVM, no Android dependency.

### query

```kotlin
public fun query(block: QueryBuilder.() -> Unit): RoomQlQuery
```

The entry point. Applies `block` to a fresh `QueryBuilder`, calls `build()`, and returns the finished query. Throws `RoomQlException` if the configured query is invalid.

```kotlin
val q: RoomQlQuery = query {
    from(UserEntityTable)
    where { UserEntityTable.age gte 18 }
}
```

### QueryBuilder

The receiver inside `query { }`. Marked `@RoomQlDsl`, so outer-scope members cannot leak into nested blocks.

| Function | Signature | Effect |
|---|---|---|
| `from` | `from(table: EntityTable)` | Sets the primary table from a generated `*Table`. Required for joins. |
| `from` | `from(tableName: String)` | Sets the primary table by raw name. Carries no column metadata, so it cannot be joined. |
| `join` | `join(table: EntityTable, type: JoinType, block: JoinScope.() -> Unit)` | Adds a JOIN with an `ON` predicate. Call repeatedly to join more than two tables. |
| `where` | `where(block: WhereScope.() -> Unit)` | Opens a `WhereScope`. Repeated calls merge into one AND-combined set. |
| `groupBy` | `groupBy(column: Column<*>)` | Groups by one column. Last call wins. |
| `having` | `having(block: HavingScope.() -> Unit)` | Opens a `HavingScope` for `HAVING`. Requires `groupBy`. |
| `orderBy` | `orderBy(expression: Expression<*>, direction: SortDirection)` | Appends a sort key. Call repeatedly for a multi-column `ORDER BY`. |
| `limit` | `limit(n: Int)` | Sets `LIMIT`. Must be positive. |
| `offset` | `offset(n: Int)` | Sets `OFFSET`. Requires `limit`. |
| `build` | `build(): RoomQlQuery` | Validates and renders. `query { }` calls this for you. |

`QueryBuilder` is **not thread-safe** — build a query on one thread or coroutine and never share a half-built builder.

### WhereScope and HavingScope: the condition operators

`WhereScope` (the receiver inside `where { }`) and `HavingScope` (the receiver inside `having { }`) declare the same operator set below. `WhereScope`'s operators are extensions on `Column<T>` — `where { }` only ever sees real columns, since SQL forbids aggregates before grouping exists. `HavingScope`'s operators are extensions on `Expression<T>`, which `Column<T>` implements, so `having { }` also accepts aggregate results. The table below shows the `Column<T>` (`WhereScope`) form; read `Column<T>` as `Expression<T>` for the `HavingScope` equivalent.

**Every value-taking condition comes in a required and an optional form**, and the name tells you which is which. The required form takes `T & Any` — Kotlin's syntax for "definitely non-null `T`" — so passing a nullable value fails to compile. The optional form is suffixed `IfNotNull` (or `IfNotEmpty` for the two list operators) and takes `T?`, skipping the condition — leaving it out of the generated SQL — when the value is absent. `isNull` and `isNotNull` take no value and never skip; they are how you match SQL `NULL` itself.

| Operator | Signature | Generated SQL |
|---|---|---|
| `eq` | `infix fun <T> Column<T>.eq(value: T & Any)` | `col = ?` |
| `eqIfNotNull` | `infix fun <T> Column<T>.eqIfNotNull(value: T?)` | `col = ?`, or skipped |
| `notEq` | `infix fun <T> Column<T>.notEq(value: T & Any)` | `col != ?` |
| `notEqIfNotNull` | `infix fun <T> Column<T>.notEqIfNotNull(value: T?)` | `col != ?`, or skipped |
| `gt` | `infix fun <T> Column<T>.gt(value: T & Any)` | `col > ?` |
| `gtIfNotNull` | `infix fun <T> Column<T>.gtIfNotNull(value: T?)` | `col > ?`, or skipped |
| `gte` | `infix fun <T> Column<T>.gte(value: T & Any)` | `col >= ?` |
| `gteIfNotNull` | `infix fun <T> Column<T>.gteIfNotNull(value: T?)` | `col >= ?`, or skipped |
| `lt` | `infix fun <T> Column<T>.lt(value: T & Any)` | `col < ?` |
| `ltIfNotNull` | `infix fun <T> Column<T>.ltIfNotNull(value: T?)` | `col < ?`, or skipped |
| `lte` | `infix fun <T> Column<T>.lte(value: T & Any)` | `col <= ?` |
| `lteIfNotNull` | `infix fun <T> Column<T>.lteIfNotNull(value: T?)` | `col <= ?`, or skipped |
| `like` | `infix fun <T : String?> Column<T>.like(value: String)` | `col LIKE ?` |
| `likeIfNotNull` | `infix fun <T : String?> Column<T>.likeIfNotNull(value: String?)` | `col LIKE ?`, or skipped |
| `notLike` | `infix fun <T : String?> Column<T>.notLike(value: String)` | `col NOT LIKE ?` |
| `notLikeIfNotNull` | `infix fun <T : String?> Column<T>.notLikeIfNotNull(value: String?)` | `col NOT LIKE ?`, or skipped |
| `contains` | `infix fun <T : String?> Column<T>.contains(value: String)` | `col LIKE ?`, bound as `%value%` |
| `containsIfNotNull` | `infix fun <T : String?> Column<T>.containsIfNotNull(value: String?)` | `col LIKE ?`, bound as `%value%`, or skipped |
| `inList` | `infix fun <T> Column<T>.inList(values: List<T & Any>)` | `col IN (?,?)`; an empty list renders `col IN ()`, which SQLite defines as matching nothing |
| `inListIfNotEmpty` | `infix fun <T> Column<T>.inListIfNotEmpty(values: List<T & Any>?)` | `col IN (?,?)`, or skipped if the list is `null` or empty |
| `notInList` | `infix fun <T> Column<T>.notInList(values: List<T & Any>)` | `col NOT IN (?)`; an empty list renders `col NOT IN ()`, which SQLite defines as matching everything |
| `notInListIfNotEmpty` | `infix fun <T> Column<T>.notInListIfNotEmpty(values: List<T & Any>?)` | `col NOT IN (?)`, or skipped if the list is `null` or empty |
| `between` | `fun <T> Column<T>.between(lower: T & Any, upper: T & Any)` | `col BETWEEN ? AND ?`. Both bounds required; no optional form — compose `gteIfNotNull` + `lteIfNotNull` for an open-ended range |
| `isNull` | `fun <T> Column<T>.isNull()` | `col IS NULL` |
| `isNotNull` | `fun <T> Column<T>.isNotNull()` | `col IS NOT NULL` |

`like`, `notLike`, and `contains` are constrained to `String` columns (`T : String?`), so they cannot be applied to a numeric column. `like` and `notLike` take the SQL pattern verbatim; `contains` adds the `%` wildcards for you.

**`T & Any` is a compile-time guarantee, not a runtime one.** Generics erase, so a `null` crossing an erased boundary — a Java caller, or an unchecked cast — is not stopped by the type at the JVM level. It is still caught: every operator above is a public function with a non-null parameter, so Kotlin compiles a `checkNotNullParameter` guard into `runtime`'s own bytecode, and such a call throws `NullPointerException` immediately rather than silently binding `NULL` into the arguments. `internal` and `private` functions do not get this guard by default; every operator here is `public`, so all of them do.

#### or

```kotlin
public fun or(block: WhereScope.() -> Unit) // and the matching overload on HavingScope
```

Groups alternatives. The group is parenthesised and combined with the surrounding conditions by `AND`. A group whose conditions all skip contributes nothing — no empty `()` is emitted.

```kotlin
where {
    UserEntityTable.status eq "active"
    or {
        UserEntityTable.age lt 18
        UserEntityTable.age gt 65
    }
}
// WHERE status = ? AND (age < ? OR age > ?)
```

### JoinScope and JoinConditionScope

```kotlin
public class JoinScope {
    public fun on(block: JoinConditionScope.() -> Unit)
}

public class JoinConditionScope {
    public infix fun <T> Column<T>.eq(other: Column<T>)
}
```

`on { }` declares the join predicate. Inside it, `eq` compares **two columns** rather than a column and a value, and both sides are rendered fully qualified (`users.id = orders.userId`). The generic bound means the two columns must hold the same type.

### Column

```kotlin
public data class Column<T>(val columnName: String, val tableName: String)
```

A typed reference to one SQL column. You do not construct these — the KSP processor generates one per entity property, and `T` carries the property's Kotlin type (including nullability) so that operators type-check against it.

### Aggregate functions

```kotlin
public fun count(column: Expression<*>): Expression<Long>
public fun countAll(): Expression<Long>
public fun <T : Number?> sum(column: Expression<T>): Expression<T?>
public fun <T : Number?> avg(column: Expression<T>): Expression<Double?>
public fun <T> min(column: Expression<T>): Expression<T?>
public fun <T> max(column: Expression<T>): Expression<T?>
```

Each returns an `Expression<T>`, so the result can be used anywhere `having { }` or `orderBy` accepts one — including compared against a value or combined with other conditions, same as a `Column`:

```kotlin
query {
    from(OrdersTable)
    groupBy(OrdersTable.customerId)
    having { count(OrdersTable.id) gt 5 }
    orderBy(sum(OrdersTable.amount), SortDirection.DESC)
}
```

`count` and `countAll` are deliberately separate: under a `LEFT JOIN`, `COUNT(column)` yields 0 for an unmatched row, while `COUNT(*)` yields 1. `sum` and `avg` are constrained to numeric column types; `avg` always returns `Double?` regardless of the input numeric type, matching SQL's own `AVG`. All six aggregate results are nullable, since SQL returns `NULL` for an empty group.

### EntityTable

```kotlin
public interface EntityTable {
    public val tableName: String
    public val allColumnNames: List<String>
}
```

Implemented by every generated `*Table` object. `allColumnNames` is what RoomQL uses to detect colliding column names across a join and to build the aliased `SELECT` list.

### RoomQlQuery

```kotlin
public data class RoomQlQuery(val sql: String, val args: List<Any?> = emptyList())
```

The DSL's output: a rendered SQL string and its positional arguments, in binding order. It is a plain-JVM value with no Android dependency, which is what lets you assert on `sql` and `args` in ordinary unit tests. Pass it to Room via [`toQuery()`](#comroomqlandroid--the-room-bridge).

### JoinType and SortDirection

```kotlin
public enum class JoinType { INNER, LEFT }
public enum class SortDirection { ASC, DESC }
```

`JoinType` renders as `INNER JOIN` or `LEFT JOIN`; RoomQL supports no other join kinds. `SortDirection` renders as `ASC` or `DESC`.

### RoomQlException

```kotlin
public class RoomQlException(message: String) : RuntimeException(message)
```

Thrown by `build()` — and therefore by `query { }` — when the configured query is invalid. Validation is deferred to build time, so a half-built builder never throws mid-DSL. These are programming errors rather than user-input errors, which is why they are unchecked.

| Mistake | Message |
|---|---|
| No `from()` | `from() must be called before build()` |
| `limit(0)` or negative | `limit() must be a positive integer, got 0` |
| `offset()` without `limit()` | `offset() requires limit() to be set` |
| `having { }` without `groupBy()` | `having() requires groupBy() to be set` |
| `join()` after a raw-string `from(String)` | `join() requires from(EntityTable) so columns can be aliased; from(String) has no column metadata` |

---

## `com.roomql.android` — the Room bridge

Artifact `io.github.kotplat.roomql:runtime-android`. Requires minSdk 21.

```kotlin
public fun RoomQlQuery.toQuery(): SupportSQLiteQuery
```

Adapts a `RoomQlQuery` into the `SupportSQLiteQuery` that Room's `@RawQuery` methods accept, by wrapping the SQL and args in a `SimpleSQLiteQuery`. This one function is the entire module, and the only point at which RoomQL touches Android.

```kotlin
userDao.search(q.toQuery())
```

---

## `com.roomql.ksp` — the code generator

Artifact `io.github.kotplat.roomql:ksp-processor`, applied with `ksp(...)`. Its only public type is the processor entry point, registered through `META-INF/services` — you never reference it in your own code.

```kotlin
public class RoomQlProcessorProvider : SymbolProcessorProvider
```

### Options

| KSP option | Default | Effect |
|---|---|---|
| `roomql.tableSuffix` | `Table` | Suffix appended to the entity class name to form the generated object's name. |

```kotlin
ksp {
    arg("roomql.tableSuffix", "Columns")   // UserEntity -> UserEntityColumns
}
```

### What the processor reads

| Annotation | Effect |
|---|---|
| `@Entity(tableName = "...")` | Sets the generated `tableName`. Without it, the class name is used. |
| `@ColumnInfo(name = "...")` | Sets the column's SQL name. The Kotlin property keeps its own name. |
| `@Ignore` | The property is skipped — it is not a column. |

Nullable properties produce a nullable `Column<T?>`.

---

## Generated code shape

For this entity:

```kotlin
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Int,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
```

the processor generates, in the **same package**:

```kotlin
object UserEntityTable : EntityTable {
    override val tableName = "users"
    override val allColumnNames = listOf("id", "name", "created_at")
    val id: Column<Int> = Column("id", "users")
    val name: Column<String> = Column("name", "users")
    val createdAt: Column<Long> = Column("created_at", "users")
}
```

Because each column is a real Kotlin symbol, renaming or deleting a property breaks every query that referenced it at **compile** time. Note that RoomQL checks column *references* only — Room's `@RawQuery` skips Room's static SQL verification, so query logic is still verified at runtime. See the [comparison in the README](../README.md#how-roomql-compares-to-the-alternatives).
