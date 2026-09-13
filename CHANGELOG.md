# Changelog

All notable changes to **RoomQL** are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and RoomQL follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

_Nothing yet._

## [1.0.0] — 2026-09-13

First stable release. Three artifacts, published on JitPack:
`roomql-runtime`, `roomql-runtime-android`, `roomql-ksp-processor`.

RoomQL builds Room queries whose filters are decided at runtime, without raw SQL strings,
reflection, or one DAO method per filter combination. A `null` filter drops out of the
generated SQL rather than matching `NULL`.

### Added

- **`query { }` DSL** (`roomql-runtime`) — builds a `RoomQlQuery` (SQL string + positional args)
  from typed column references, with no Android dependency.
- **Null-skipping condition operators** — `eq`, `notEq`, `gt`, `gte`, `lt`, `lte`, `like`,
  `notLike`, `contains`, `inList`, `notInList`, `between`, `isNull`, `isNotNull`. A `null` value
  (or an empty list) removes the condition from the generated SQL entirely.
- **`or { }` grouping**, `orderBy` (multi-column), `limit`/`offset`, `groupBy`/`having`.
- **`join(EntityTable, JoinType) { on { } }`** with `INNER` and `LEFT`, chainable across more
  than two tables.
- **Automatic JOIN column aliasing** — column names colliding across joined tables are emitted as
  `users.id AS users__id`, and references to them are table-qualified in `WHERE`, `HAVING`,
  `GROUP BY`, and `ORDER BY`. Unique names stay bare.
- **KSP processor** (`roomql-ksp-processor`) — generates an `object <EntityName>Table` with a typed
  `Column<T>` per column for every Room `@Entity`. Honours `@Entity(tableName)`, `@ColumnInfo(name)`,
  and skips `@Ignore`d properties. Object name suffix configurable via the `roomql.tableSuffix` option.
- **`RoomQlQuery.toQuery()`** (`roomql-runtime-android`) — adapts the DSL output to the
  `SupportSQLiteQuery` Room's `@RawQuery` methods accept.
- **Consumer R8 rules** — `roomql-runtime-android` ships `consumer-rules.pro`, packaged into the aar
  as `proguard.txt`. It carries no keep rules, because RoomQL needs none: table and column names are
  baked into generated code as string literals, so obfuscation cannot alter the SQL the DSL emits.
  CI scans the published artifacts for reflection on every run, so the guarantee is enforced rather
  than asserted.
- **`explicitApi()` and Binary Compatibility Validator** on all three published modules, so the public
  API surface is checked in CI (`./gradlew apiCheck`).
- **Published POM metadata** — name, description, license, developer, SCM, and issue-tracker fields
  on all three artifacts.

### Documentation and examples

- **[Usage guide](docs/USAGE.md)** — every capability as a worked example, with the SQL each generates.
- **[API reference](docs/API.md)** — every public type, function, and operator with its signature,
  generated SQL, and null-skipping behaviour.
- **Runnable examples** — `MinimalExample.kt` (the whole setup in one annotated file, with its own
  test), the `:sample` module's Robolectric coverage against in-memory Room, and a Compose demo app.
  These live in the repository only; none of them is published, and no example code ships inside the
  three artifacts.

### Validation

`build()` throws `RoomQlException` for: a missing `from()`, a non-positive `limit()`, an `offset()`
without `limit()`, a `having()` without `groupBy()`, and a `join()` on a raw-string `from(String)`
(which carries no column metadata to alias with, and would otherwise emit a silently unaliased
`SELECT *`).

### Not included in v1

Annotation-driven DAO generation. KSP cannot read function bodies, so the query and its
`observedEntities` cannot be inferred from an annotated method. The exploration lives on the
`development` branch and is tracked for v2 in
[#6](https://github.com/ahmednobii/RoomQL/issues/6) and
[#13](https://github.com/ahmednobii/RoomQL/issues/13).

[1.0.0]: https://github.com/ahmednobii/RoomQL/releases/tag/1.0.0
