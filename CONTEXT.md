# RoomQl

A Kotlin library providing a type-safe DSL for building dynamic Android Room queries at runtime. It eliminates raw `@Query` SQL strings and combinatorial DAO overloads by compiling a fluent builder expression to a `SupportSQLiteQuery` that Room's `@RawQuery` accepts.

## Language

### DSL Types

**Column**:
A typed, KSP-generated reference to a SQL column in a Room entity table, parameterized by the Kotlin property type (`Column<Int>`, `Column<String>`). Carries the resolved SQL column name and the entity table name. The primary thing a consumer writes in a DSL expression — never a bare string.
_Avoid_: column name, column ref, column string, field

**Condition**:
A sealed type representing a single SQL predicate or a logical grouping of predicates, produced by applying an operator to a Column. The DSL collects Conditions inside `where { }` and `having { }` blocks. A null-valued Condition is silently dropped at build time.
_Avoid_: filter, predicate, constraint, expression, clause

**QueryBuilder**:
The DSL receiver inside a `query { }` block. Accumulates clauses — FROM, WHERE, JOIN, ORDER BY, GROUP BY, HAVING, LIMIT, OFFSET — and serializes them to a `SupportSQLiteQuery` when `build()` is called. Validation is lazy: errors are deferred to `build()`, not raised per setter call.
_Avoid_: builder, query object, query DSL

**ConditionScope**:
The receiver type for `where { }` and `having { }` blocks. Collects Conditions as AND-combined by default; `or { }` within a ConditionScope produces a single OR-grouped Condition.
_Avoid_: where block, filter scope, condition builder

### Integration Modes

**Mode A**:
The KSP-generated integration path. The developer annotates an abstract function inside an existing `@Dao` interface with `@QueryFunction`; KSP generates a concrete `@RawQuery`-wired implementation automatically. Zero `@RawQuery` boilerplate in developer code.
_Avoid_: generated mode, annotation mode, auto mode, KSP mode

**Mode B**:
The manual integration path. The developer writes a `@RawQuery` DAO function themselves and calls `query { }` in their repository or data-source layer to build the argument, passing the resulting `SupportSQLiteQuery` to the DAO. No KSP involvement.
_Avoid_: manual mode, raw mode, hand-rolled mode

**Generated DAO Impl**:
The concrete class (`RoomQl_<DaoName>.kt`) that the KSP processor produces for Mode A. It lives in the same package as the `@Dao` interface, extends it, and provides `@RawQuery`-annotated Room implementations for each `@QueryFunction` function. Room's own KSP then processes this class as a normal DAO.
_Avoid_: generated class, KSP output, DAO stub

### Room Integration

**observedEntities**:
The list of Room `@Entity` classes whose table changes trigger re-emission of a `Flow`-returning `@RawQuery`. In Mode A, the KSP processor infers this list from the `from()` and `join()` entity arguments in the DSL. In Mode B, the developer declares it manually on `@RawQuery`.
_Avoid_: watched entities, reactive entities, tracked tables, entity list

**Column Aliasing**:
The automatic generation of `table.column AS table__column` SQL aliases by the QueryBuilder when two or more joined tables share a column name. Prevents the silent data-overwrite bug that occurs when a raw JOIN cursor has duplicate column names.
_Avoid_: column renaming, deduplication, alias generation

**RoomQlException**:
The single runtime exception type thrown by the library. A subclass of `RuntimeException`. Raised at `build()` time for invalid QueryBuilder state (e.g. no `from()`, negative `limit`, `offset` without `limit`). Never thrown during the builder phase before `build()` is called.
_Avoid_: DSL exception, query error, build error
