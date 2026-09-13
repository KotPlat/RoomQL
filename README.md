<p align="center">
  <img src="docs/banner.svg" alt="RoomQL — type-safe Kotlin DSL for dynamic Android Room queries" width="860"/>
</p>

<h1 align="center">RoomQL</h1>

<p align="center"><b>A type-safe Kotlin DSL for building dynamic Android Room queries at runtime — no raw SQL strings, no reflection, no 2ⁿ DAO methods.</b></p>

<p align="center">
  <a href="https://jitpack.io/#ahmednobii/RoomQL"><img src="https://img.shields.io/jitpack/v/github/ahmednobii/RoomQL?label=JitPack&color=3DDC84" alt="JitPack version"/></a>
  <a href="https://github.com/ahmednobii/RoomQL/actions/workflows/ci.yml"><img src="https://github.com/ahmednobii/RoomQL/actions/workflows/ci.yml/badge.svg" alt="CI status"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-blue.svg" alt="Apache 2.0 licensed"/></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.0.x-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.0.x"/></a>
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/minSdk-21%2B-3DDC84?logo=android&logoColor=white" alt="minSdk 21+"/></a>
</p>

<p align="center">
  <a href="#installation"><b>Install</b></a> &nbsp;·&nbsp;
  <a href="#minimal-working-example"><b>Quick start</b></a> &nbsp;·&nbsp;
  <a href="docs/USAGE.md"><b>Usage guide</b></a> &nbsp;·&nbsp;
  <a href="docs/API.md"><b>API reference</b></a> &nbsp;·&nbsp;
  <a href="#how-roomql-compares-to-the-alternatives"><b>Comparison</b></a> &nbsp;·&nbsp;
  <a href="#faq"><b>FAQ</b></a> &nbsp;·&nbsp;
  <a href="CHANGELOG.md"><b>Changelog</b></a>
</p>

---

Room has no good answer for a query whose filters are decided at runtime. Write it as a `@Query` string and you end up with `WHERE (:minAge IS NULL OR age >= :minAge)` repeated per filter, with column names the compiler never checks — rename a column and the query breaks *silently at runtime*. Write it as overloaded DAO methods and you need one method per filter combination, heading toward 2ⁿ. **RoomQL** builds the SQL programmatically instead: you keep Room's `@RawQuery`, a KSP processor generates a typed `Column<T>` for every column in your `@Entity` classes, and a `null` filter simply drops out of the generated SQL.

## Installation

RoomQL 1.0.0 publishes through JitPack. Add the repository in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Then declare the three artifacts (version catalog form, `gradle/libs.versions.toml`):

```toml
[versions]
roomql = "1.0.0"

[libraries]
roomql-runtime         = { module = "com.github.ahmednobii.RoomQL:roomql-runtime",         version.ref = "roomql" }
roomql-runtime-android = { module = "com.github.ahmednobii.RoomQL:roomql-runtime-android", version.ref = "roomql" }
roomql-ksp-processor   = { module = "com.github.ahmednobii.RoomQL:roomql-ksp-processor",   version.ref = "roomql" }
```

```kotlin
plugins {
    id("com.google.devtools.ksp")
}

dependencies {
    implementation(libs.roomql.runtime)           // the query { } DSL (pure JVM)
    implementation(libs.roomql.runtime.android)   // the .toQuery() bridge to Room
    ksp(libs.roomql.ksp.processor)                // generates the *Table objects
}
```

> **1.0.0 is not tagged yet.** Until the release tag lands, JitPack has nothing to resolve — build the library locally with `VERSION=1.0.0 ./gradlew publishToMavenLocal` and put `mavenLocal()` ahead of JitPack in your repository list.

All three are required on Android. `:runtime` is a plain-JVM module so the DSL stays unit-testable without an emulator; `:runtime-android` is the thin adapter that turns its output into the `SupportSQLiteQuery` Room wants. See [Modules](#modules) for what each one contains.

### Requirements

| Dependency | Version |
|---|---|
| Kotlin | 2.0.x |
| KSP | `2.0.21-1.0.28` |
| Room | 2.6.x – 2.7.x |
| Android | minSdk 21+ |
| JDK | 17 |

## Minimal working example

```kotlin
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.roomql.android.toQuery
import com.roomql.runtime.SortDirection
import com.roomql.runtime.query

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val age: Int,
    val status: String,
)
// KSP generates UserEntityTable in the same package.

@Dao
interface UserDao {
    @RawQuery
    fun search(q: SupportSQLiteQuery): List<UserEntity>
}

fun searchUsers(dao: UserDao, minAge: Int?, status: String?): List<UserEntity> {
    val q = query {
        from(UserEntityTable)
        where {
            UserEntityTable.age gte minAge      // dropped from the SQL when minAge is null
            UserEntityTable.status eq status    // dropped from the SQL when status is null
        }
        orderBy(UserEntityTable.age, SortDirection.DESC)
        limit(20)
    }
    return dao.search(q.toQuery())
}
```

`searchUsers(dao, 18, "active")` runs `SELECT * FROM users WHERE age >= ? AND status = ? ORDER BY age DESC LIMIT 20`.
`searchUsers(dao, 18, null)` runs `SELECT * FROM users WHERE age >= ? ORDER BY age DESC LIMIT 20`.
`searchUsers(dao, null, null)` runs `SELECT * FROM users ORDER BY age DESC LIMIT 20`.

No `if` ladders, no `IS NULL OR` trick, and `UserEntityTable.age` is a real Kotlin symbol — rename the property or the column and the code stops compiling.

## What you get

- **Compile-time safety on column references.** `UserEntityTable.age` is generated from your entity by KSP. Renames and typos fail the build instead of the query.
- **Nullable filters that disappear.** A `null` value drops that condition from the SQL entirely.
- **A real DSL, not string concatenation.** `where`, `or { }`, `orderBy`, `limit`/`offset`, `groupBy`/`having`, `join`. Values are bound as positional `?` parameters, so there is no injection surface.
- **Automatic JOIN column aliasing.** Colliding column names across joined tables are aliased (`users.id AS users__id`) so a cursor never silently overwrites one column with another.
- **Works with Room's own `@RawQuery`.** The output is a plain `SupportSQLiteQuery`. Blocking, `suspend`, and `Flow` return types all work — Room does the rest.

## Documentation

RoomQL's reference documentation lives alongside the code, not on a separate site:

| Document | What it covers |
|---|---|
| **[Usage Guide](docs/USAGE.md)** | Every capability as a worked example — entities, `@RawQuery` DAOs, AND/OR, optional filters, joins, `Flow`, error handling, testing — with the SQL each one generates. |
| **[API Reference](docs/API.md)** | Every public type, function, and operator with its signature, generated SQL, and null-skipping behaviour. |
| **[Changelog](CHANGELOG.md)** | What shipped in each release. |
| **[Contributing](CONTRIBUTING.md)** | Build, test, and the `apiDump` step for public API changes. |

Per-module notes: [`:runtime`](runtime/README.md) · [`:runtime-android`](runtime-android/README.md) · [`:ksp-processor`](ksp-processor/README.md)

### Public API at a glance

RoomQL's entire public surface, across all three artifacts. Signatures, generic bounds, and the SQL each operator generates are in the **[API Reference](docs/API.md)**.

| Symbol | Artifact | What it does |
|---|---|---|
| [`query { }`](docs/API.md#query) | runtime | Entry point. Builds and returns a `RoomQlQuery`. |
| [`QueryBuilder`](docs/API.md#querybuilder) | runtime | Receiver inside `query { }`: `from`, `join`, `where`, `groupBy`, `having`, `orderBy`, `limit`, `offset`, `build`. |
| [`ConditionScope`](docs/API.md#conditionscope-the-condition-operators) | runtime | Receiver inside `where { }` / `having { }`. Carries every operator below, plus `or { }` for alternatives. |
| `eq`, `notEq`, `gt`, `gte`, `lt`, `lte` | runtime | Comparisons. Each skips itself when the value is `null`. |
| `like`, `notLike`, `contains` | runtime | Text matching on `String` columns. `contains` adds the `%` wildcards for you. |
| `inList`, `notInList` | runtime | Set membership. Skips when the list is `null` or empty. |
| `between` | runtime | Range. Skips when either bound is `null`. |
| `isNull`, `isNotNull` | runtime | SQL `NULL` checks — the two operators that never skip. |
| [`Column<T>`](docs/API.md#column) | runtime | A typed column reference. Generated per entity property, never hand-written. |
| [`EntityTable`](docs/API.md#entitytable) | runtime | Implemented by every generated `*Table`: `tableName`, `allColumnNames`. |
| [`RoomQlQuery`](docs/API.md#roomqlquery) | runtime | The DSL's output: `sql` plus positional `args`. Pure JVM — assert on it in unit tests. |
| [`JoinType`](docs/API.md#jointype-and-sortdirection) / [`SortDirection`](docs/API.md#jointype-and-sortdirection) | runtime | `INNER`/`LEFT`, and `ASC`/`DESC`. |
| [`RoomQlException`](docs/API.md#roomqlexception) | runtime | Thrown by `build()` for an invalid query, with the reason in the message. |
| [`RoomQlQuery.toQuery()`](docs/API.md#comroomqlandroid--the-room-bridge) | runtime-android | Adapts the DSL output into the `SupportSQLiteQuery` Room's `@RawQuery` accepts. |
| [`roomql.tableSuffix`](docs/API.md#options) | ksp-processor | KSP option renaming the generated objects' `Table` suffix. |

## How RoomQL compares to the alternatives

| Approach | Column names checked? | Runtime-optional filters | Cost |
|---|---|---|---|
| **RoomQL** | Yes, via generated refs | Native — `null` drops the condition | Three artifacts, a `.toQuery()` call, no whole-query SQL validation |
| Room `@Query` string | No | `(:x IS NULL OR col = :x)` per filter | Room validates the whole SQL at compile time — a real advantage RoomQL gives up |
| Overloaded DAO methods | Via Room's `@Query` checking | One method per combination (2ⁿ) | Unmaintainable past a few filters |
| Hand-built `SimpleSQLiteQuery` | No — you concatenate strings | Manual `if` ladders | Zero dependencies; every injection and arg-ordering bug is yours |
| `SupportSQLiteQueryBuilder` (androidx.sqlite) | No — columns are strings | Manual | Already on your classpath; no type safety, no Room integration |
| [SQLDelight](https://github.com/sqldelight/sqldelight) | Yes — full SQL verified at compile time | Limited; dynamic shapes need generated variants or raw execution | You leave Room entirely and own the schema in `.sq` files |

**Use RoomQL when** you are staying on Room and have search-style queries whose filters are decided at runtime. **Do not use RoomQL when** your queries are static — a plain `@Query` gives you full compile-time SQL verification, which RoomQL cannot, because `@RawQuery` skips it by design. It is also the wrong tool if you need a query shape RoomQL does not build: column projections, `DISTINCT`, aggregate expressions, multi-column `GROUP BY`, subqueries, or `UNION`. RoomQL selects whole rows.

## FAQ

### Does RoomQL replace Room?

No. RoomQL 1.0.0 sits on top of Room and produces the `SupportSQLiteQuery` that Room's own `@RawQuery` methods take. You keep your `@Entity` classes, your `@Database`, your DAOs, and your migrations exactly as they are — RoomQL only replaces the SQL string for queries whose filters vary at runtime.

### How do I build a Room query with optional filters in Kotlin?

Wrap the filters in RoomQL's `query { }` block and pass the nullable values straight in: `where { UserEntityTable.age gte minAge }` emits `age >= ?` when `minAge` has a value and emits nothing at all when it is `null`. There is no `if` ladder and no `(:minAge IS NULL OR age >= :minAge)` trick, because the condition is never added to the SQL in the first place.

### Does RoomQL use reflection or runtime code generation?

No. RoomQL's KSP processor generates a `<EntityName>Table` object with a typed `Column<T>` per column at **build** time, and the runtime is a plain string builder over those references. Nothing is reflected over or generated while the app runs, so R8/ProGuard needs no extra keep rules. Column and table names are baked in as string literals, so obfuscation cannot change the SQL RoomQL emits — CI enforces this by scanning the published artifacts for reflection on every run.

### Is RoomQL safe from SQL injection?

Values are always bound as positional `?` parameters and passed to SQLite as an argument list — RoomQL never interpolates a user-supplied value into the SQL text. Table and column names come from generated code rather than user input. The one exception is the raw-string `from("table_name")` overload: never pass an untrusted string to it.

### Why do I need three artifacts instead of one?

The DSL (`roomql-runtime`) is a pure-JVM module with no Android dependency, which is what lets you unit-test generated SQL on the JVM with no emulator or Robolectric. `roomql-runtime-android` is the thin Android bridge holding only `RoomQlQuery.toQuery()`, and `roomql-ksp-processor` runs at build time only. Splitting them keeps the Android dependency out of your test path.

### Does RoomQL work with Kotlin Multiplatform?

Not in 1.0.0. `roomql-runtime` is a plain JVM module (not a KMP source set), and `roomql-runtime-android` depends on `androidx.sqlite`. RoomQL targets Android and JVM projects that use Room.

### Does RoomQL support KAPT?

No — RoomQL 1.0.0 ships a KSP processor only, so your module needs the `com.google.devtools.ksp` plugin and `ksp(libs.roomql.ksp.processor)`. Room itself can still run on KAPT in the same module if you have not migrated it yet.

### Why doesn't my `Flow` re-emit when the table changes?

Room cannot infer which tables a raw query touches, so a `Flow`-returning `@RawQuery` must list them itself: `@RawQuery(observedEntities = [UserEntity::class])`. If that list is missing or names the wrong entity, the `Flow` emits once and then goes quiet. This is a Room requirement, not a RoomQL one.

### Does RoomQL validate my SQL at compile time?

Only the column references. RoomQL's KSP processor makes `UserEntityTable.age` a real Kotlin symbol, so a renamed or deleted column is a compile error — but Room's `@RawQuery` deliberately skips Room's static SQL verification, so a logically wrong query (a bad join predicate, a `groupBy` that does not match the projection) surfaces at runtime. For static queries, a plain `@Query` remains the safer choice.

## Pitfalls and limitations

Know these before adopting:

- **Nullable-skipping cuts both ways.** Passing `null` *removes* the condition; it does not mean “match `NULL`”. Use `isNull()` for that. A `null` filter can silently return more rows than you expect.
- **No compile-time SQL validation of the whole query.** Column references are checked; query logic is not. See the FAQ above.
- **JOIN results need matching `@ColumnInfo` names.** Colliding columns are aliased to `table__column`; your result class must use those exact names or the field will not map.
- **`Flow` `observedEntities` is manual.** Get it wrong and the `Flow` will not re-emit.
- **You must call `.toQuery()`.** The DSL output is a plain-JVM `RoomQlQuery`; `.toQuery()` (from `:runtime-android`) adapts it. One call at the DAO boundary is the price of an emulator-free test path.
- **Positional `?` args only.** No named parameters — a Room `@RawQuery` restriction.
- **`QueryBuilder` is not thread-safe.** Build a query on one thread or coroutine; never share a half-built builder.
- **Validation is deferred to `build()`.** Missing `from()`, a non-positive `limit`, `offset` without `limit`, and `having` without `groupBy` all throw `RoomQlException` at build time, not while you configure.
- **Whole-row selects only.** No projections, `DISTINCT`, aggregate expressions, multi-column `GROUP BY`, subqueries, or `UNION`.
- **Room version range.** Targets Room 2.6.x–2.7.x (API 21+). Room 2.8 raised `minSdk` to 23; support is deferred.
- **No auto-generated JOIN result types.** You supply your own result class — by design, so you control its shape.

## Modules

| Module | Artifact | What it holds |
|---|---|---|
| [`:runtime`](runtime) | `roomql-runtime` | the `query { }` DSL, `Column<T>`, conditions — pure JVM |
| [`:runtime-android`](runtime-android) | `roomql-runtime-android` | `RoomQlQuery.toQuery()` → `SupportSQLiteQuery` |
| [`:ksp-processor`](ksp-processor) | `roomql-ksp-processor` | generates the `*Table` objects from `@Entity` |

> **On annotation-driven integration.** RoomQL 1.0.0 uses Room's manual `@RawQuery`: you declare the method, build with `query { }`, and pass `.toQuery()`. A zero-boilerplate annotation-driven integration was explored and dropped — KSP cannot read function bodies, so it could not infer the query or `observedEntities`. That work lives on the `development` branch and is tracked for v2 in issues [#6](https://github.com/ahmednobii/RoomQL/issues/6) and [#13](https://github.com/ahmednobii/RoomQL/issues/13). v1 ships no annotation artifact.

## Contributing and support

Bug reports, feature requests, and questions all go to [GitHub Issues](https://github.com/ahmednobii/RoomQL/issues). See [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request, [CHANGELOG.md](CHANGELOG.md) for release notes, and [SECURITY.md](SECURITY.md) to report a vulnerability privately.

## License

Licensed under the [Apache License, Version 2.0](LICENSE). Copyright 2026 Ahmed Nobi.

You may use, modify, and redistribute this software (including commercially), provided you retain the copyright notice, the [`LICENSE`](LICENSE), and the [`NOTICE`](NOTICE) attribution, and state any changes you make.
