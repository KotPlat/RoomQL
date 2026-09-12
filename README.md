<p align="center">
  <img src="docs/banner.svg" alt="RoomQL — type-safe room query dsl" width="860"/>
</p>

A type-safe Kotlin DSL for building **dynamic** Room queries at runtime — no raw SQL strings, no reflection, no combinatorial explosion of `@Query` methods.

> **Building dynamic Room queries with optional filters?** If you've fought the `@Query("... WHERE (:minAge IS NULL OR age >= :minAge)")` trick, watched a DAO sprout one method per filter combination (2ⁿ and counting), or had a renamed column break a query *silently at runtime*, RoomQL is for you. It composes Room's `SupportSQLiteQuery` at runtime through a compile-time-safe Kotlin builder — you keep `@RawQuery`, RoomQL builds the SQL.

---

## What it is

RoomQL is a small library (three modules) that lets you compose SQLite queries with a Kotlin builder:

```kotlin
val q = query {
    from(UserEntityTable)
    where {
        UserEntityTable.age gte minAge      // skipped entirely if minAge is null
        UserEntityTable.status eq status    // skipped entirely if status is null
    }
    orderBy(UserEntityTable.age, SortDirection.DESC)
    limit(20)
}
dao.search(q.toQuery())
```

A KSP processor reads your `@Entity` classes and generates a typed `Column<T>` reference for every column, so `UserEntityTable.age` is a real Kotlin symbol — rename the property or column and the query stops compiling instead of failing at runtime.

## Why — the problem

Room gives you two ways to write a query with optional runtime filters, and both break down:

1. **Raw `@Query` strings.** SQL lives in an annotation string. The compiler can't see column names, so a renamed column fails *silently at runtime*. Dynamic filters force fragile `WHERE (:minAge IS NULL OR age >= :minAge)` tricks that are hard to read and easy to get wrong.
2. **Overloaded DAO methods.** One method per filter combination. With *n* optional filters you head toward *2ⁿ* methods — unmaintainable past a handful, and still impossible for open-ended combinations.

Neither scales with the number of optional parameters. RoomQL removes both by building the SQL programmatically, with the column names checked by the compiler.

## What you get

- **Compile-time safety on column references.** `UserEntityTable.age` is generated from your entity. Renames and typos are caught at build time.
- **Nullable filters that disappear.** A `null` value drops that condition from the SQL — no `if` ladders, no `IS NULL OR` tricks.
- **A real DSL, not string concatenation.** `where`, `or { }`, `orderBy`, `limit`/`offset`, `groupBy`/`having`, `join`. Positional `?` binding is handled for you (no injection surface).
- **Automatic JOIN column aliasing.** When you join using `from(SomeTable)` (the generated `*Table`, not `from("table_name")`), colliding column names across joined tables are aliased (`users.id AS users__id`) so a raw cursor never silently overwrites one column with another.
- **Works with Room's own `@RawQuery`.** The output is a plain `SupportSQLiteQuery`; Room does the rest. Suspend and `Flow` return types are supported.

## Installation

RoomQL publishes via JitPack. Add the repository to `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
    }
}
```

The full setup is three artifacts (version catalog form):

```toml
[versions]
roomql = "0.1.0"

[libraries]
roomql-runtime         = { module = "com.github.ahmednobii.RoomQL:roomql-runtime",          version.ref = "roomql" }
roomql-runtime-android = { module = "com.github.ahmednobii.RoomQL:roomql-runtime-android",  version.ref = "roomql" }
roomql-ksp-processor   = { module = "com.github.ahmednobii.RoomQL:roomql-ksp-processor",    version.ref = "roomql" }
```

```kotlin
implementation(libs.roomql.runtime)          // the DSL (pure JVM)
implementation(libs.roomql.runtime.android)   // the .toQuery() bridge to Room
ksp(libs.roomql.ksp.processor)                // generates the *Table objects
```

> `:runtime` is a plain-JVM module (the DSL and its `RoomQlQuery` output). `:runtime-android` is a thin Android module that adapts a `RoomQlQuery` into the `SupportSQLiteQuery` Room needs. You need both on Android; the split keeps the DSL unit-testable without an emulator.

### Requirements

| Dependency | Version |
|---|---|
| Kotlin | 2.0.x (KSP `2.0.21-1.0.28`) |
| Room | 2.6.x–2.7.x |
| Android | minSdk 21+ |
| JDK | 17 |

## Usage

> For a detailed, example-driven walkthrough — every operator, the SQL each query generates, JOIN mapping, Flow, error handling, and a full repository — see the **[Usage Guide](docs/USAGE.md)**.

### 1. Annotate entities as usual

```kotlin
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val age: Int,
    val status: String,
)
// KSP generates: object UserEntityTable : TableColumns { id; name; age; status; ... }
```

`@ColumnInfo(name = "...")` and `@Entity(tableName = "...")` are respected — the generated refs use the real SQL names.

### 2. Declare a `@RawQuery` DAO method

RoomQL uses Room's own `@RawQuery`. You own the DAO surface:

```kotlin
@Dao
interface UserDao {
    @RawQuery
    fun search(query: SupportSQLiteQuery): List<UserEntity>

    @RawQuery
    suspend fun searchSuspend(query: SupportSQLiteQuery): List<UserEntity>

    // Flow requires observedEntities so Room knows which tables to watch:
    @RawQuery(observedEntities = [UserEntity::class])
    fun observe(query: SupportSQLiteQuery): Flow<List<UserEntity>>
}
```

Relevant imports:

```kotlin
import com.roomql.runtime.query          // the query { } builder
import com.roomql.runtime.SortDirection
import com.roomql.runtime.JoinType
import com.roomql.android.toQuery         // RoomQlQuery -> SupportSQLiteQuery
// UserEntityTable etc. are generated in the same package as your @Entity
```

### 3. Build the query and hand it to the DAO

```kotlin
fun searchUsers(minAge: Int?, status: String?): List<UserEntity> {
    val q = query {
        from(UserEntityTable)
        where {
            UserEntityTable.age gte minAge      // dropped if null
            UserEntityTable.status eq status    // dropped if null
        }
        orderBy(UserEntityTable.age, SortDirection.DESC)
    }
    return userDao.search(q.toQuery())
}
```

Call `searchUsers(18, "active")` → both filters apply. Call `searchUsers(18, null)` → only the age filter is in the SQL. Call `searchUsers(null, null)` → `SELECT * FROM users ORDER BY age DESC`.

### Operators

Inside `where { }` (and `having { }`):

| Operator | Applies to | Null behaviour |
|---|---|---|
| `eq`, `notEq` | any `Column<T>` | skipped when null |
| `gt`, `gte`, `lt`, `lte` | any `Column<T>` | skipped when null |
| `like`, `notLike`, `contains` | any `Column<T>` (String pattern) | skipped when null |
| `inList`, `notInList` | any `Column<T>` | skipped when null or empty |
| `between(lo, hi)` | any `Column<T>` | skipped when either bound is null |
| `isNull()`, `isNotNull()` | any `Column<T>` | never skipped |

Conditions are `AND`-combined by default. Group `OR`s explicitly:

```kotlin
where {
    UserEntityTable.status eq "active"
    or {
        UserEntityTable.age lt 18
        UserEntityTable.age gt 65
    }
}
// ... WHERE status = ? AND (age < ? OR age > ?)
```

### JOINs

```kotlin
val q = query {
    from(UserEntityTable)
    join(OrderEntityTable, JoinType.INNER) {
        on { UserEntityTable.id eq OrderEntityTable.userId }
    }
}
orderDao.usersWithOrders(q.toQuery())
```

When two joined tables share a column name (e.g. both have `id` and `status`), RoomQL aliases them as `users__id`, `orders__id`, etc. — **provided the primary table is given as a generated `*Table`** (`from(UserEntityTable)`). The raw-string `from("users")` overload has no column metadata to alias with, so combining it with `join(...)` throws `RoomQlException` at `build()` rather than silently falling back to an unaliased `SELECT *`. Your result POJO must map the aliased names for colliding columns:

```kotlin
data class UserOrder(
    @ColumnInfo(name = "name")  val userName: String,   // unique → un-aliased
    @ColumnInfo(name = "total") val orderTotal: Double,  // unique → un-aliased
    // for a colliding column you would use @ColumnInfo(name = "users__id")
)
```

`JoinType.INNER` and `JoinType.LEFT` are supported; `join` is chainable for more than two tables.

### Pagination, grouping

```kotlin
query {
    from(OrderEntityTable)
    groupBy(OrderEntityTable.userId)
    having { OrderEntityTable.total gt 100.0 }
    limit(50)
    offset(100)
}
```

## Pitfalls & limitations

Be aware of these before adopting:

- **Nullable-skipping cuts both ways.** Passing `null` *removes* the condition — it does **not** mean “match `NULL`”. If you actually want `WHERE status IS NULL`, use `isNull()`. A `null` filter can silently return more rows than you expect.
- **No compile-time SQL validation of the whole query.** Room's `@RawQuery` deliberately skips Room's static SQL verification. Column *references* are type-checked (that's the KSP part), but a logically wrong query (bad `groupBy`, wrong join predicate) won't be caught until it runs.
- **JOIN results need matching `@ColumnInfo` names.** Colliding columns are aliased to `table__column`; your result POJO must use those exact names or the field won't map.
- **`Flow` `observedEntities` is manual.** Room can't infer which tables a raw query touches, so you must list them on `@RawQuery(observedEntities = [...])`. Get this wrong and the `Flow` won't re-emit (or watches the wrong table).
- **You must call `.toQuery()`.** The DSL output (`RoomQlQuery`) is a plain-JVM type; `.toQuery()` (from `:runtime-android`) adapts it to `SupportSQLiteQuery`. This split is what keeps the DSL unit-testable off-device — the cost is one call at the DAO boundary.
- **Positional `?` args only.** No named parameters (a Room `@RawQuery` restriction).
- **`QueryBuilder` is not thread-safe.** Build a query on one thread/coroutine; don't share a half-built builder.
- **Validation is deferred to `build()`.** Illegal states (no `from()`, non-positive `limit`, `offset` without `limit`, `having` without `groupBy`) throw `RoomQlException` when the query is built, not while you're configuring it.
- **Room version range.** Targets Room 2.6.x–2.7.x (API 21+). Room 2.8 raised `minSdk` to 23; support is deferred.
- **No auto-generated JOIN result types.** You supply your own result data class (by design — you control the shape).

## Modules

| Module | Artifact | What it holds |
|---|---|---|
| `:runtime` | `roomql-runtime` | the `query { }` DSL, `Column<T>`, conditions — pure JVM |
| `:runtime-android` | `roomql-runtime-android` | `RoomQlQuery.toQuery()` → `SupportSQLiteQuery` |
| `:ksp-processor` | `roomql-ksp-processor` | generates the `*Table` objects |

> **Integration.** RoomQL v1 uses Room's manual `@RawQuery` (you declare the method, call `query { }`, pass `.toQuery()`). A zero-boilerplate annotation-driven integration was explored and dropped — KSP cannot read function bodies, so it couldn't infer the query or `observedEntities`. That work now lives on the `development` branch and is tracked for v2 in issues #6 / #13; v1 ships no annotation artifact.

## Working example

The [`:sample`](sample) module is a runnable, tested end-to-end reference: real `@Entity` classes, a `@Database`, manual `@RawQuery` DAOs, and Robolectric tests that exercise the whole stack (generated `*Table` → `query { }` → `.toQuery()` → in-memory Room) — including nullable-filter skipping, JOIN mapping, and `Flow` re-emission. Run it with:

```
./gradlew :sample:testDebugUnitTest
```

## License

Licensed under the [Apache License, Version 2.0](LICENSE). Copyright 2026 Ahmed Nobi.

You may use, modify, and redistribute this software (including commercially), provided you retain the copyright notice, the `LICENSE`, and the [`NOTICE`](NOTICE) attribution, and state any changes you make.
