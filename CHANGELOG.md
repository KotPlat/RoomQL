# Changelog

All notable changes to **RoomQL** are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and RoomQL follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

_Nothing yet._

## [1.0.0] — unreleased

First stable release. Three artifacts, published on JitPack:
`roomql-runtime`, `roomql-runtime-android`, `roomql-ksp-processor`.

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
- **`:sample` module** — a runnable, Robolectric-tested end-to-end reference covering nullable-filter
  skipping, JOIN mapping, and `Flow` re-emission.
- **`explicitApi()` and Binary Compatibility Validator** on all three published modules, so the public
  API surface is checked in CI (`./gradlew apiCheck`).
- **Published POM metadata** — name, description, license, developer, SCM, and issue-tracker fields
  on all three artifacts.

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

[Unreleased]: https://github.com/ahmednobii/RoomQL/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/ahmednobii/RoomQL/releases/tag/v1.0.0
