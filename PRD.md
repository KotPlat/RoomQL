# RoomQl — Product Requirements Document

## Problem Statement

Android developers using Room are forced into one of two painful patterns whenever query conditions must be determined at runtime: they write raw SQL strings embedded in `@Query` annotations (losing type safety and IDE support), or they maintain a combinatorial explosion of overloaded DAO methods — one for each combination of optional filters. Neither approach scales. As the number of dynamic filter parameters grows, both patterns become unmanageable: raw strings break silently on schema changes, and overloaded methods multiply quadratically. There is no first-party Room API that lets a developer compose a query programmatically with the same safety guarantees they get from `@Query`.

## Solution

RoomQl is a JitPack-publishable Kotlin library that gives Android developers a type-safe, KSP-powered DSL for building dynamic Room queries at runtime. A developer writes a fluent `query { }` builder expression; KSP generates column-reference companion properties from `@Entity` classes so every column name is a typed compile-time symbol; the builder compiles down to a `SupportSQLiteQuery` value that Room's `@RawQuery` accepts directly. Null filter values are silently skipped — no if-guards needed in calling code. The library offers two integration modes: a zero-boilerplate KSP-generated `@RawQuery` wiring path (Mode A) and a manual `@RawQuery` path for developers who want full control (Mode B). Both modes coexist freely in the same `@Dao` interface.

## User Stories

1. As an Android developer, I want to write `query { from(UserEntity) where { UserEntity.age gt 18 } }` in Kotlin, so that I can construct a type-safe Room query without writing raw SQL strings.
2. As an Android developer, I want column references like `UserEntity.age` to be generated automatically from my `@Entity` class, so that renaming a column in Room produces a compile error in my DSL query rather than a silent runtime failure.
3. As an Android developer, I want null filter values to be automatically skipped in the WHERE clause, so that I can pass `null` for optional filters without wrapping every condition in an `if` statement.
4. As an Android developer, I want to use `eq`, `notEq`, `gt`, `gte`, `lt`, `lte`, `like`, `notLike`, `isNull`, `isNotNull`, `inList`, `notInList`, `between`, and `contains` as infix operators on column references, so that common SQL predicates feel idiomatic in Kotlin.
5. As an Android developer, I want to nest conditions inside `or { }` blocks, so that I can express OR-grouped predicates without leaving the DSL.
6. As an Android developer, I want to call `orderBy(UserEntity.name, ASC)` and chain multiple `orderBy` calls, so that I can specify multi-column sort orders without writing ORDER BY strings.
7. As an Android developer, I want to call `limit(n)` and `offset(n)` as builder calls, so that I can paginate results without string interpolation.
8. As an Android developer, I want to call `groupBy(UserEntity.status)` and `having { count() gt 5 }`, so that I can write aggregate queries with GROUP BY and HAVING without raw SQL.
9. As an Android developer, I want to write `join(OrderEntity, INNER) { on { UserEntity.id eq OrderEntity.userId } }`, so that I can JOIN tables and have column-name collisions aliased automatically.
10. As an Android developer, I want to annotate a function inside my existing `@Dao` interface with `@QueryFunction` and have KSP generate the `@RawQuery` implementation automatically (Mode A), so that I get type-safe dynamic queries with zero boilerplate.
11. As an Android developer, I want to write my own `@RawQuery` DAO method and call the `query { }` builder manually to produce its argument (Mode B), so that I retain full control over the Room `@Dao` interface when I need it.
12. As an Android developer, I want Mode A and Mode B to coexist freely inside the same `@Dao` interface, so that I can migrate method-by-method without breaking existing code.
13. As an Android developer, I want a `@QueryFunction`-annotated function that returns `Flow<List<T>>` to have `observedEntities` inferred automatically from my `from()` and `join()` clauses, so that reactive queries react to table changes without me declaring entities manually.
14. As an Android developer, I want `suspend` and `Flow` return types to both be supported through the single `query { }` entry point, so that the return type on my DAO function drives the behaviour rather than a separate builder variant.
15. As an Android developer using Mode B with Flow, I want to keep full manual control over `@RawQuery(observedEntities = [...])`, so that I can override or extend the automatic inference when needed.
16. As an Android developer, I want KSP to emit a clear compile error when I annotate a function with `@QueryFunction` on an interface that is not annotated with `@Dao`, so that misconfiguration is caught at build time not runtime.
17. As an Android developer, I want KSP to emit a clear compile error when I return `Flow` from a `@QueryFunction` but the entities cannot be inferred from the query DSL, so that I know exactly what to fix before the app runs.
18. As an Android developer, I want KSP to emit a clear compile error when a `@QueryFunction` function has a return type that the library does not support, so that unsupported patterns fail loudly at build time.
19. As an Android developer, I want runtime validation to be lazy — deferred until `build()` is called — so that partial builder state during construction does not throw prematurely.
20. As an Android developer, I want runtime errors to be thrown as a `RoomQlException` (a `RuntimeException` subclass), so that I can catch library errors specifically without catching unrelated exceptions.
21. As an Android developer, I want the library to support Android API 21+ with Room 2.6.x–2.7.x, so that it is compatible with the widest possible device base.
22. As an Android developer, I want to add the library via JitPack with a single `implementation` line, so that I can integrate it without setting up a local Maven repository.
23. As an Android developer, I want the KSP processor and the DSL runtime to work together transparently so that I only need a single `ksp()` dependency declaration for the RoomQl processor alongside Room's own KSP processor.
24. As an Android developer, I want KSP-generated column ref files (`UserEntity_Columns.kt`) placed in the same package as my entity class, so that `UserEntity.age` is usable without extra imports.
25. As an Android developer, I want `@Embedded` fields in my `@Entity` class to be flattened into column refs with their correct prefix, so that embedded properties are as accessible as top-level ones in the DSL.
26. As an Android developer, I want `@ColumnInfo(name = "...")` annotations to be respected when generating column refs, so that the DSL uses the actual SQL column name rather than the Kotlin property name.
27. As an Android developer, I want JOIN queries to auto-alias conflicting column names in the generated SQL, so that I do not get silent data-overwrite bugs when two joined tables share a column name.
28. As an Android developer, I want to provide my own result data class for JOIN queries rather than using a library-generated type, so that I control the shape and names of joined result data.
29. As an Android developer building a multi-module project, I want the DSL to live in `:core:query-dsl` and for only `:data` layer modules to depend on it, so that the Gradle module graph enforces my clean-architecture boundary.

## Implementation Decisions

### Gradle Module Structure

Three Gradle modules:

- **`:annotations`** — zero dependencies; contains only annotation classes (`@QueryFunction`, `@RoomQlEntity`). Consumed by application modules that declare annotations.
- **`:runtime`** — depends on Room (`androidx.room:room-runtime`, `androidx.room:room-ktx`); contains the `query { }` DSL builder, `Column<T>` type, all condition/operator classes, and the `SupportSQLiteQuery` compilation logic.
- **`:ksp-processor`** — depends on KSP API and KotlinPoet; scans `@Entity` and `@QueryFunction` annotations and generates column-ref companion files and Mode A DAO implementations. Never depended on by application code at runtime.

Both the RoomQl KSP processor and Room's own KSP processor run in the same `ksp()` invocation. KSP aggregating mode is required for the processor because cross-entity JOIN compatibility checks may need all entities visible at once.

### Column Reference Generation

KSP generates a file `UserEntity_Columns.kt` in the same package as the entity. The file declares companion extension properties so references read as `UserEntity.age`. The generated type is `Column<T>` — a value holding the SQL column name string (resolved from `@ColumnInfo(name = ...)` when present, otherwise the Kotlin property name) and the Kotlin type token. `@Embedded` fields are flattened recursively using Room's own prefix logic.

`Column<T>` exposes all infix operators (`eq`, `gt`, `inList`, etc.) that produce `Condition` values. It also carries a companion table-name property used by the DSL for automatic JOIN alias generation.

From prototype (ticket 004):
```kotlin
query {
  from(UserEntity)
  where {
    UserEntity.age gt age          // null → skipped
    UserEntity.status eq status    // null → skipped
    or {
      UserEntity.role eq "admin"
      UserEntity.role eq "mod"
    }
  }
  orderBy(UserEntity.name, ASC)
  limit(20)
  offset(40)
}
```

### DSL Entry Point and Builder Semantics

`query { }` is a top-level function whose receiver is `QueryBuilder`. It returns a `SupportSQLiteQuery`. AND is the default combinator inside `where { }`; OR groups are expressed with an explicit `or { }` block. Null arguments to condition operators cause that condition to be omitted silently — no if-checks needed at the call site.

Operators: `eq`, `notEq`, `gt`, `gte`, `lt`, `lte`, `like`, `notLike`, `isNull`, `isNotNull`, `inList`, `notInList`, `between`, `contains` (sugar for `LIKE %value%`). `orderBy` is chainable. `limit` and `offset` are sibling builder calls. `groupBy` and `having { }` are sibling blocks.

Binding uses positional `?` placeholders only (Room's `@RawQuery` restriction). No named-parameter binding.

### JOIN Syntax

```kotlin
join(OrderEntity, JoinType.INNER) {
  on { UserEntity.id eq OrderEntity.userId }
}
```

Chains are unlimited depth. The DSL auto-aliases conflicting column names in the generated SQL. The developer provides a result data class; no KSP-generated merge type is produced.

### Dual DAO Integration Modes

**Mode A** — annotate an abstract function inside an existing `@Dao` interface with `@QueryFunction`. KSP generates a concrete `RoomQl_UserDao.kt` in the same package that extends the `@Dao` interface and provides a `@RawQuery` implementation for that function. Room's own KSP then processes this generated class normally.

**Mode B** — the developer writes `@RawQuery fun rawSearch(q: SupportSQLiteQuery): List<T>` in their own `@Dao` interface, and calls `query { }` in repository/data-source code to build the argument. No KSP involvement.

Both modes coexist freely in the same `@Dao` interface.

### Flow Integration

The `query { }` entry point is shared for both one-shot (`suspend`) and reactive (`Flow`) queries. The return type declared on the `@QueryFunction`-annotated function determines which path Room takes. For Mode A with a `Flow` return type, KSP infers `observedEntities` from the `from()` and `join()` clause entity types in the DSL and populates `@RawQuery(observedEntities = [...])` on the generated implementation automatically. Mode B Flow queries keep full manual control of `observedEntities`.

Room's observable `@RawQuery` with `observedEntities` is stable from Room 2.2.0 onward.

### Error Model

KSP compile-time errors (reported via `KSPLogger.error(message, symbol)` pointing at the annotation site):

- `@QueryFunction` applied to a function on an interface not annotated with `@Dao`
- `@QueryFunction` function returns `Flow` but entities cannot be inferred from the DSL body
- `@QueryFunction` function has an unsupported return type
- Missing return type on a `@QueryFunction` function

Runtime errors throw `RoomQlException : RuntimeException`. Validation is lazy — deferred to `build()` time, not raised per setter call. Examples of runtime-only catches: negative `LIMIT` value, `OFFSET` without `LIMIT`, empty `from()` when `build()` is called.

### KSP Code Generation Tooling

KotlinPoet + `kotlinpoet-ksp` for all code generation. No raw string templates. Aggregating KSP mode required. Generated files are placed in the same package as the source entity or DAO, not in a `generated.roomquery` sub-package.

### Publishing

JitPack, GitHub-tag-based release. SemVer tags. No Gradle plugin — dependency wiring is documented in the README.

## Testing Decisions

### What Makes a Good Test

Tests should assert the external observable output of a module — the SQL string and argument list emitted by `build()`, or the correctness of KSP-generated source files — not internal builder state or private methods. A good test does not reach into the DSL builder's internals to verify intermediate condition lists; it calls `build()` and asserts the resulting `SupportSQLiteQuery.sql` and `SupportSQLiteQuery.argCount` (or the bound argument array obtained via reflection on `SimpleSQLiteQuery`).

### Testing Seams

**Primary seam — `SupportSQLiteQuery` output (`:runtime` module, pure JVM)**

The single output interface of the DSL is `SupportSQLiteQuery` (specifically `SimpleSQLiteQuery` from `androidx.sqlite`). Tests at this seam exercise the entire DSL grammar — WHERE composition, JOIN, ORDER BY, GROUP BY, HAVING, LIMIT/OFFSET, null-skipping — without requiring an Android device or database. This is the highest-value seam: one seam covers the entire DSL surface.

Tests call `query { ... }.build()` (or equivalent), then assert `.sql` (the SQL string) and the ordered argument list. Each DSL operator variant and combinator gets one representative test. Edge cases — null inputs, empty `where {}`, chained `orderBy`, `offset` without `limit` — are also covered at this seam.

**Secondary seam — KSP-generated source files (`:ksp-processor` module, JVM compilation harness)**

Use `kotlin-compile-testing-ksp` (Tschuchort's compilation testing library) to feed source files containing `@Entity` and `@QueryFunction` annotations through the RoomQl KSP processor in a JVM test, then assert the content of generated files. Tests verify: correct SQL column name resolution from `@ColumnInfo`, `@Embedded` field flattening with prefix, Mode A DAO impl class name and `@RawQuery` annotation, `observedEntities` inference for Flow return types, and all KSP compile-error paths.

No Android instrumented tests are required for the library itself. The sample app serves as the end-to-end integration harness.

### Prior Art for Tests

No existing tests in the repo yet. The `kotlin-compile-testing-ksp` pattern is well-established in the Kotlin annotation processing community (Room itself uses a similar harness) and should be used as the reference model for processor tests.

## Out of Scope

- Replacing Room's compile-time `@Query` validation — this library handles the *dynamic* case only; static queries remain as `@Query`.
- Supporting non-Room databases (SQLDelight, raw SQLite, Realm, or any JDBC-based ORM).
- An IDE plugin or language injection for the DSL.
- A Gradle plugin for automatic multi-module wiring (deferred to v2).
- Pagination integration with `androidx.paging` — `LIMIT/OFFSET` stays raw in v1.
- Multi-entity return type generation for JOINs — developers supply their own result data class.
- Room 2.8.0+ support (raises minSdk to API 23; deferred until the API 21 constraint is relaxed).
- SNAPSHOT / pre-release publishing workflow (SemVer tags only for v1).

## Further Notes

- Room pins to ≤2.7.x to preserve API 21 support. Room 2.8.0 raised `minSdk` to 23.
- The closest prior art is `AniTrend/support-query-builder`, which uses bare `String` column refs. RoomQl's typed `Column<T>` refs are the primary differentiator.
- jOOQ's `noCondition()` seed pattern and Ktorm's `whereWithConditions {}` block form were studied and informed the null-skipping and combinator design.
- The silent JOIN column-name collision bug (Row data overwrites silently when two joined tables share a column name) is addressed by auto-aliasing in the DSL output.
- `@Transaction` is required alongside `@RawQuery` when the query uses `@Relation`; this is a Room constraint that the library documents but does not enforce.
- KSP minimum version: `1.9.20-1.0.14` (aligns with Room 2.6's Kotlin 1.9 line).
