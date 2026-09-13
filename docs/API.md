# RoomQL API Reference

Every public type, function, and operator in **RoomQL 1.0.0** — the type-safe Kotlin DSL for building dynamic Android Room queries — with the SQL each one generates. For worked examples see the [Usage Guide](USAGE.md); for installation and the overall pitch see the [main README](../README.md).

The public surface is deliberately small and is frozen by [Binary Compatibility Validator](https://github.com/Kotlin/binary-compatibility-validator), so everything RoomQL exposes is on this page.

---

## Contents

- [`com.roomql.runtime` — the DSL](#comroomqlruntime--the-dsl)
  - [query](#query)
  - [QueryBuilder](#querybuilder)
  - [ConditionScope: the condition operators](#conditionscope-the-condition-operators)
  - [JoinScope and JoinConditionScope](#joinscope-and-joinconditionscope)
  - [Column](#column)
  - [EntityTable](#entitytable)
  - [RoomQlQuery](#roomqlquery)
  - [JoinType and SortDirection](#jointype-and-sortdirection)
  - [RoomQlException](#roomqlexception)
- [`com.roomql.android` — the Room bridge](#comroomqlandroid--the-room-bridge)
- [`com.roomql.ksp` — the code generator](#comroomqlksp--the-code-generator)
- [Generated code shape](#generated-code-shape)

---

## `com.roomql.runtime` — the DSL

Artifact `roomql-runtime`. Pure JVM, no Android dependency.

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
| `where` | `where(block: ConditionScope.() -> Unit)` | Opens a `ConditionScope`. Repeated calls merge into one AND-combined set. |
| `groupBy` | `groupBy(column: Column<*>)` | Groups by one column. Last call wins. |
| `having` | `having(block: ConditionScope.() -> Unit)` | Opens a `ConditionScope` for `HAVING`. Requires `groupBy`. |
| `orderBy` | `orderBy(column: Column<*>, direction: SortDirection)` | Appends a sort key. Call repeatedly for a multi-column `ORDER BY`. |
| `limit` | `limit(n: Int)` | Sets `LIMIT`. Must be positive. |
| `offset` | `offset(n: Int)` | Sets `OFFSET`. Requires `limit`. |
| `build` | `build(): RoomQlQuery` | Validates and renders. `query { }` calls this for you. |

`QueryBuilder` is **not thread-safe** — build a query on one thread or coroutine and never share a half-built builder.

### ConditionScope: the condition operators

The receiver inside `where { }` and `having { }`. Every operator is an extension on `Column<T>`, so it registers itself in the enclosing scope automatically.

**Every value-taking operator accepts a nullable value and emits nothing when it is `null`.** This is RoomQL's core contract: an absent filter is absent from the SQL, rather than matching `NULL`.

| Operator | Signature | Generated SQL | Skips when |
|---|---|---|---|
| `eq` | `infix fun <T> Column<T>.eq(value: T?)` | `col = ?` | value is `null` |
| `notEq` | `infix fun <T> Column<T>.notEq(value: T?)` | `col != ?` | value is `null` |
| `gt` | `infix fun <T> Column<T>.gt(value: T?)` | `col > ?` | value is `null` |
| `gte` | `infix fun <T> Column<T>.gte(value: T?)` | `col >= ?` | value is `null` |
| `lt` | `infix fun <T> Column<T>.lt(value: T?)` | `col < ?` | value is `null` |
| `lte` | `infix fun <T> Column<T>.lte(value: T?)` | `col <= ?` | value is `null` |
| `like` | `infix fun <T : String?> Column<T>.like(value: String?)` | `col LIKE ?` | value is `null` |
| `notLike` | `infix fun <T : String?> Column<T>.notLike(value: String?)` | `col NOT LIKE ?` | value is `null` |
| `contains` | `infix fun <T : String?> Column<T>.contains(value: String?)` | `col LIKE ?`, bound as `%value%` | value is `null` |
| `inList` | `infix fun <T> Column<T>.inList(values: List<T>?)` | `col IN (?,?)` | list is `null` or empty |
| `notInList` | `infix fun <T> Column<T>.notInList(values: List<T>?)` | `col NOT IN (?)` | list is `null` or empty |
| `between` | `fun <T> Column<T>.between(lower: T?, upper: T?)` | `col BETWEEN ? AND ?` | either bound is `null` |
| `isNull` | `fun <T> Column<T>.isNull()` | `col IS NULL` | never |
| `isNotNull` | `fun <T> Column<T>.isNotNull()` | `col IS NOT NULL` | never |

`like`, `notLike`, and `contains` are constrained to `String` columns (`T : String?`), so they cannot be applied to a numeric column. `like` and `notLike` take the SQL pattern verbatim; `contains` adds the `%` wildcards for you.

#### or

```kotlin
public fun or(block: ConditionScope.() -> Unit)
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

`JoinType` renders as `INNER JOIN` or `LEFT JOIN`; RoomQL 1.0.0 supports no other join kinds. `SortDirection` renders as `ASC` or `DESC`.

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

Artifact `roomql-runtime-android`. Requires minSdk 21.

```kotlin
public fun RoomQlQuery.toQuery(): SupportSQLiteQuery
```

Adapts a `RoomQlQuery` into the `SupportSQLiteQuery` that Room's `@RawQuery` methods accept, by wrapping the SQL and args in a `SimpleSQLiteQuery`. This one function is the entire module, and the only point at which RoomQL touches Android.

```kotlin
userDao.search(q.toQuery())
```

---

## `com.roomql.ksp` — the code generator

Artifact `roomql-ksp-processor`, applied with `ksp(...)`. Its only public type is the processor entry point, registered through `META-INF/services` — you never reference it in your own code.

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
