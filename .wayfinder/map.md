# RoomQl — Wayfinder Map

`wayfinder:map`

## Destination

A publishable JitPack Kotlin library named **RoomQl** that gives Android developers a type-safe, KSP-powered DSL for building dynamic Room queries at runtime — replacing raw `@Query` strings and multi-overload workarounds with a fluent builder that compiles to `SupportSQLiteQuery`, enforces clean-architecture layer boundaries at the Gradle module level, and supports both KSP-generated and manual `@RawQuery` DAO integration modes.

Done means: all design decisions are locked, the module structure is scaffolded, and the implementation path is clear enough to hand off.

## Notes

- **Platform**: Kotlin + Android Room (API 21+ / Room 2.6.x–2.7.x — Room 2.8+ raised minSdk to 23)
- **DSL shape**: Kotlin builder → `SupportSQLiteQuery` (used with `@RawQuery`)
- **Type safety**: KSP generates column refs from `@Entity` classes (`UserEntity.Columns.age`)
- **Query scope v1**: SELECT/WHERE/AND/OR, ORDER BY + LIMIT/OFFSET, JOIN (inner/left), GROUP BY + HAVING
- **Async**: suspend + Flow (via Room's observable `@RawQuery` with `observedEntities`)
- **Layer enforcement**: Gradle multi-module — DSL lives in `:core:query-dsl`, only `:data` modules depend on it
- **DAO integration**: two modes — KSP-generated `@RawQuery` wiring AND manual `@RawQuery` — developer chooses
- **Error model**: KSP compile-time where possible + runtime `IllegalArgumentException` for dynamic edge cases
- **Publishing**: JitPack (GitHub-tag-based release)
- **Skills to invoke**: `/grilling`, `/domain-modeling`, `/prototype`, `/research`

## Decisions so far

- [Gradle Module Structure](tickets/005-module-structure-grilling.md) — 3 modules: `:annotations` (zero deps), `:runtime` (DSL + Room), `:ksp-processor` (KSP + code gen); both KSP processors run in the same `ksp()` invocation; Gradle dependency graph enforces the clean-arch boundary
- [Prior Art Survey](tickets/003-prior-art-survey.md) — SQLDelight/Exposed/Ktorm/JOOQ all JDBC-only (Android-incompatible); `AniTrend/support-query-builder` is closest prior art but uses bare `String` column refs; key lessons: jOOQ's `noCondition()` seed, Ktorm's block-form `whereWithConditions {}`, typed `Column<T>` refs (not strings)
- [KSP Entity Scanning Research](tickets/002-ksp-entity-scanning-research.md) — use `Resolver.getSymbolsWithAnnotation("androidx.room.Entity")`; replicate Room's `@ColumnInfo` name logic; `@Embedded` fields flatten recursively with prefix; aggregating KSP mode required; min KSP `1.9.20-1.0.14` (Room 2.6 = Kotlin 1.9 line)
- [Room @RawQuery + Flow Research](tickets/001-room-rawquery-research.md) — no SQL restrictions beyond SQLite; positional `?` only; `observedEntities` required on Flow return (compile error if omitted); silent JOIN column-name collision bug (must alias); `@Transaction` required with `@Relation`; Flow stable since Room 2.2.0; **Room 2.8.0 raised minSdk to API 23 — pin to Room ≤2.7.x to keep API 21**
- [DSL Grammar Design](tickets/004-dsl-grammar-prototype.md) — entry point `query { }`; column refs as `UserEntity.age` (KSP-generated companion extensions); AND-by-default with `or { }` groups; null auto-skips condition; operators: eq/notEq, gt/gte/lt/lte, like/notLike, isNull, inList, between, contains; `orderBy(col, DIR)`; `limit(n)` + `offset(n)`; `groupBy(col)` + `having { }`
- [JOIN Syntax Design](tickets/007-join-syntax-design.md) — `join(Entity, INNER|LEFT) { on { col eq col } }`; unlimited chain depth; DSL auto-aliases conflicting column names in generated SQL; developer provides result data class (no KSP-generated merge type)
- [Dual DAO Integration Design](tickets/008-dual-dao-integration-design.md) — Mode A: `@QueryFunction` on a fun inside existing `@Dao` interface; KSP generates `@RawQuery` impl for that method. Mode B: developer writes `@RawQuery` once manually, calls `query { }` builder to produce the arg. Both modes coexist freely in the same `@Dao`.
- [Flow + @RawQuery Integration](tickets/009-flow-integration-design.md) — KSP infers `observedEntities` from `from()` / `join()` clauses automatically; single `query { }` entry point for both one-shot and reactive (return type drives behaviour); Mode B Flow queries keep full manual `@RawQuery(observedEntities=[...])` control
- [Error Model Design](tickets/010-error-model-design.md) — KSP compile errors for: missing return type, Flow without inferable entity, `@QueryFunction` on non-`@Dao`, unsupported return type; runtime throws custom `RoomQlException(RuntimeException)`; validation is lazy (at `build()` time, not per-setter)
- [KSP Code Generation Design](tickets/011-ksp-codegen-design.md) — column refs generated as companion extensions in same package (`UserEntity_Columns.kt`); `Column<T>(columnName, type)` core type + KSP-generated table name companion property for JOIN aliasing; Mode A DAO impl as `RoomQl_UserDao.kt` in same package extending the `@Dao` interface; KotlinPoet + kotlinpoet-ksp for all code generation

## Not yet specified

- Testing strategy for the KSP processor (instrumented vs. unit-test compilation harness)
- JitPack release workflow and versioning scheme (SemVer tags, SNAPSHOT builds)
- Sample app or README demo — what the minimal "hello world" query looks like end-to-end
- Pagination integration — whether `LIMIT/OFFSET` pairs with `androidx.paging` or stays raw
- Multi-entity return type shape for JOINs — data class, sealed type, or tuple?

## Out of scope

- Replacing Room's compile-time `@Query` validation — this library handles the *dynamic* case only; static queries stay as `@Query`
- Supporting non-Room databases (SQLDelight, SQLite directly, Realm)
- An IDE plugin / language injection for the DSL
- Gradle plugin for automatic multi-module wiring (can be a v2 effort)
