# Room @RawQuery + SupportSQLiteQuery Deep Dive

`wayfinder:research` | status: closed | assignee: claude
parent: map.md | blocks: 004-dsl-grammar-prototype, 006-flow-integration-design

## Question

What are the exact constraints and capabilities of Room's `@RawQuery` + `SupportSQLiteQuery` escape hatch that this library will be built on?

Specifically:

1. What SQL dialects / clauses does `SupportSQLiteQuery` accept? Any restrictions vs. raw SQLite?
2. How does `@RawQuery` interact with Kotlin Flow — what is the `observedEntities` parameter and how must it be populated for reactive queries to work?
3. How does `@RawQuery` handle multi-entity return types (for JOINs)? Can it return a data class that spans two tables?
4. What are the thread-safety / transaction requirements for `@RawQuery`?
5. Are there known edge cases or bugs in Room's `@RawQuery` that the library must work around?
6. What Room version introduced stable `@RawQuery` + Flow support? (Validate the API 21 / Room 2.6 baseline.)

Surface the Room source, KDoc, and any official migration guides relevant to these questions.

## Resolution

_Researched 2026-09-05 against primary sources._

### 1. SQL clauses SupportSQLiteQuery accepts

`SupportSQLiteQuery` is a thin interface in `androidx.sqlite.db` with three methods: `getSql()` (returns the raw SQL string), `bindTo(SupportSQLiteProgram)` (binds positional args), and `getArgCount()`. It imposes **no SQL restrictions** on top of SQLite itself. Any clause combination that SQLite accepts works: SELECT with WHERE, JOIN, GROUP BY, HAVING, ORDER BY, LIMIT, OFFSET, UNION, sub-queries, CTEs, window functions, etc.

**Room does not validate the SQL at build time.** The official `@RawQuery` docs state:

> "Room will generate the code based on the return type of the function and failure to pass a proper query will result in a runtime failure or an undefined result."
> "If you know the query at compile time, you should always prefer `@Query` since it validates the query at compile time."

All SQL errors (typos, wrong table names, wrong column references) surface as a runtime `SQLiteException` — never at compile time.

**Bind parameter restriction:** Only positional `?` placeholders are supported. Named parameters (`:name`, `@name`, `$name`) are not supported by `SimpleSQLiteQuery.bind()`, which only iterates positional indices.

**Type mapping for `SimpleSQLiteQuery` bind args:**

| Kotlin/Java type | SQLite binding |
|---|---|
| `null` | `bindNull()` |
| `ByteArray` | `bindBlob()` |
| `Float`/`Double` | `bindDouble()` |
| `Long`/`Int`/`Short`/`Byte` | `bindLong()` |
| `String` | `bindString()` |
| anything else | throws `IllegalArgumentException` at runtime |

**DML via `@RawQuery` is unsupported on the DAO codepath.** The generated code expects a result cursor; INSERT/UPDATE/DELETE through a `@RawQuery` DAO method produces undefined results. Raw DML must go through `RoomDatabase.openHelper.writableDatabase.execSQL(...)`.

Sources: [android.arch.persistence.room.RawQuery API reference](https://developer.android.com/reference/android/arch/persistence/room/RawQuery); [SimpleSQLiteQuery API reference](https://developer.android.com/reference/android/arch/persistence/db/SimpleSQLiteQuery)

---

### 2. @RawQuery + Kotlin Flow (`observedEntities`)

`observedEntities` is a `KClass<*>` array attribute on `@RawQuery`:

```kotlin
@RawQuery(observedEntities = [User::class, Book::class])
abstract fun getResults(query: SupportSQLiteQuery): Flow<List<MyResult>>
```

Official KDoc:

> "Denotes the list of entities which are accessed in the provided query and should be observed for invalidation if the query is observable."
> "The listed classes should either be annotated with `@Entity` or they should reference to at least 1 Entity (via `@Embedded` or `@Relation`)."
> "Providing this field in a non-observable query has no impact."

**What happens when `observedEntities` is left empty on a reactive return type:**

Room's annotation processor **rejects it at compile time** with:

> "Observable query return type... can only be used with SELECT queries that directly or indirectly access at least one table. For @RawQuery, you should specify the list of tables to be observed via the observedEntities field."

This is a build error, not a runtime failure.

**How it works internally:** Room maintains an `InvalidationTracker` backed by a `room_table_modification_log` table. When any listed entity's underlying table is modified, `InvalidationTracker` notifies all registered observers, Room re-executes the `@RawQuery` SQL, and the Flow emits the new value.

**Silent correctness bug:** Listing the wrong entities compiles successfully but means the Flow never re-emits when the actual tables change. There is no runtime warning.

**FTS / non-entity table workaround:** When the query targets a table with no corresponding `@Entity` (e.g., an FTS virtual table shadow table), the documented workaround is to supply a "fake" entity — one that changes whenever the relevant data changes — purely to satisfy the compiler check.

**Flow became stable for `@RawQuery` in Room 2.2.0 (October 9, 2019).** It was introduced in alpha in Room 2.2.0-alpha02 (August 7, 2019): *"Query methods can now return Flow types that re-emit when observed tables change."*

Sources: [android.arch.persistence.room.RawQuery API reference](https://developer.android.com/reference/android/arch/persistence/room/RawQuery); [Room AndroidX releases](https://developer.android.com/jetpack/androidx/releases/room)

---

### 3. JOIN / multi-table return types

Yes — `@RawQuery` can return a POJO that spans two or more tables. Two patterns are documented:

**Pattern A — `@Embedded` with `@Relation`:**

```kotlin
data class UserAndAllPets(
    @Embedded val user: User,
    @Relation(parentColumn = "id", entityColumn = "userId")
    val pets: List<Pet>
)

@Dao interface RawDao {
    @Transaction
    @RawQuery
    fun getUsersAndAllPets(query: SupportSQLiteQuery): List<UserAndAllPets>
}
```

`@Relation` does **not** rely on JOIN columns being present in the cursor — Room issues a separate sub-query per parent row. `@Transaction` is required with `@Relation` to prevent inconsistent reads across those implicit sub-queries.

**Pattern B — Flat POJO with column-name-matched fields:**

```kotlin
data class NameAndLastName(val name: String, val lastName: String)

@Dao interface RawDao {
    @RawQuery
    fun get(query: SupportSQLiteQuery): NameAndLastName
}
```

Room maps cursor column names to field names (case-insensitive).

**Column alias requirement:** When joined tables share a column name (e.g., both have `id`), the second column silently overwrites the first in Room's cursor-to-POJO mapping — **no exception is thrown, data is silently wrong.** You must alias in the SQL:

```sql
SELECT u.id AS userId, b.id AS bookId, u.name, b.title
FROM users u JOIN books b ON u.id = b.userId
```

**Multimap return types (`Map<User, List<Book>>`) introduced in Room 2.4.0 are supported only by `@Query`, not `@RawQuery`.**

Sources: [android.arch.persistence.room.RawQuery API reference](https://developer.android.com/reference/android/arch/persistence/room/RawQuery); [ProAndroidDev – Room lessons from multiple-table joins](https://proandroiddev.com/room-database-lessons-learnt-from-working-with-multiple-tables-d499c9be94ce)

---

### 4. Thread-safety and transactions

**Main-thread prohibition:** Room enforces the same main-thread check for `@RawQuery` as for all other DAO methods. Calling on the main thread throws:

> `IllegalStateException: Cannot access database on the main thread since it may potentially lock the UI for a long period of time.`

This check is bypassed only if `RoomDatabase.Builder.allowMainThreadQueries()` is set (strongly discouraged).

**`suspend fun` dispatch:** A `suspend @RawQuery` function automatically dispatches to Room's IO `CoroutineDispatcher` via `CoroutinesRoom.execute()`. The caller does not need to switch dispatchers manually. Suspend support was added in Room 2.1.0-alpha03 (December 4, 2018), stable in Room 2.1.0.

**Flow queries:** Room always executes the underlying SQL on its IO thread, regardless of the calling coroutine's dispatcher.

**`@Transaction` compatibility:** `@RawQuery` and `@Transaction` compose correctly:

```kotlin
@Transaction
@RawQuery(observedEntities = [User::class])
abstract fun getUsersAndPets(query: SupportSQLiteQuery): Flow<List<UserAndAllPets>>
```

`@Transaction` wraps the generated code in `beginTransaction()` / `setTransactionSuccessful()` / `endTransaction()`. Required whenever the return type contains `@Relation` fields.

Sources: [CommonsWare Room – Room and the Main Application Thread](https://commonsware.com/Room/pages/chap-roomthreads-001.html); [Room Transaction API reference](https://developer.android.com/reference/android/arch/persistence/room/Transaction); [Florina Muntenescu – Room + Coroutines](https://medium.com/androiddevelopers/room-coroutines-422b786dc4c5)

---

### 5. Edge cases and known bugs

**1. No compile-time SQL validation (by design).** All SQL errors — typos, wrong column names, type mismatches — surface only at runtime as `SQLiteException` or silently empty/wrong results. Unlike `@Query`, there is no annotation-processor SQL parse step.

**2. Silent data corruption from column-name collisions in JOINs.** When two joined tables share a column name, Room silently uses the last occurrence. No warning, no exception. Mitigation: always alias ambiguous columns in the SQL string.

**3. SQL injection risk.** `SimpleSQLiteQuery` must be used with `?` placeholders and a `bindArgs` array. String concatenation into the SQL literal is not sanitised by Room at any layer.

**4. Wrong `observedEntities` is a silent correctness bug.** Listing entities that don't correspond to the tables the query reads from compiles fine. The Flow simply never re-emits when those tables change. No runtime error or warning.

**5. Bug — `suspend @RawQuery` with generic return type in a base DAO (Room 2.x).** Room failed to correctly identify the return type of an inherited `suspend fun` annotated with `@RawQuery` in a generic base DAO. Fixed; filed as [Google Issue Tracker #137878827](https://issuetracker.google.com/issues/137878827).

**6. Bug — incorrect error message for `suspend @RawQuery` with no return type.** Room emitted a misleading compile error when a `suspend` DAO function annotated with `@RawQuery` had no return type. Fixed in a subsequent patch.

**7. `SimpleSQLiteQuery.bind()` throws `IllegalArgumentException` for unsupported types.** Passing `Enum`, `Boolean`, or any non-primitive non-string to `bindArgs` throws at runtime. Room does not auto-convert these.

**8. `SupportSQLiteQuery` is Android-only; KMP projects need `RoomRawQuery`.** Room 2.7.0-alpha06 (August 7, 2024) introduced `RoomRawQuery` as the cross-platform equivalent for Kotlin Multiplatform projects. `SupportSQLiteQuery` remains the Android-only API. Source: [Room release notes – 2.7.0-alpha06](https://developer.android.com/jetpack/androidx/releases/room)

---

### 6. Version history — stable @RawQuery + Flow; API 21 support

| Milestone | Version | Date |
|---|---|---|
| `@RawQuery` first appeared (arch, `String` param) | 1.1.0-beta1 | March 21, 2018 |
| `@RawQuery` stable with `SupportSQLiteQuery` (arch) | 1.1.0 | May 8, 2018 |
| `@RawQuery` stable in AndroidX namespace | 2.0.0 | October 1, 2018 |
| `suspend fun` `@RawQuery` (alpha) | 2.1.0-alpha03 | December 4, 2018 |
| `suspend fun` `@RawQuery` stable | 2.1.0 | mid-2019 |
| `Flow` + `@RawQuery` (alpha) | 2.2.0-alpha02 | August 7, 2019 |
| **`Flow` + `@RawQuery` stable** | **2.2.0** | **October 9, 2019** |
| Room 2.6.0 stable | 2.6.0 | October 18, 2023 |
| Room 2.8.0 raises minSdk to API 23 | 2.8.0-rc02 | August 27, 2025 |

**`@RawQuery` `String`-accepting form was removed before stable** — Room 1.1.0-beta1 accepted a raw `String`; this was changed before 1.1.0 stable in favour of `SupportSQLiteQuery`.

**Room 2.6.x and minSdk 21 (API 21): YES, fully supported.** Room 2.6.x maintained API 21 as its minimum SDK. The minimum was raised to **API 23** in Room 2.8.0 (September 10, 2025), citing [b/380448311](https://issuetracker.google.com/issues/380448311). Any project that must target minSdk 21 must stay on Room 2.7.x or earlier.

Sources: [Room AndroidX releases](https://developer.android.com/jetpack/androidx/releases/room); [Architecture Components Release Notes Archive](https://developer.android.com/jetpack/androidx/releases/archive/arch)

---

### Sources

- [android.arch.persistence.room.RawQuery API reference](https://developer.android.com/reference/android/arch/persistence/room/RawQuery)
- [androidx.room package-summary](https://developer.android.com/reference/androidx/room/package-summary)
- [Room AndroidX releases](https://developer.android.com/jetpack/androidx/releases/room)
- [Architecture Components Release Notes Archive](https://developer.android.com/jetpack/androidx/releases/archive/arch)
- [Write asynchronous DAO queries](https://developer.android.com/training/data-storage/room/async-queries)
- [SupportSQLiteQuery API reference (android.arch)](https://developer.android.com/reference/android/arch/persistence/db/SupportSQLiteQuery)
- [SimpleSQLiteQuery API reference (android.arch)](https://developer.android.com/reference/android/arch/persistence/db/SimpleSQLiteQuery)
- [Room Transaction API reference](https://developer.android.com/reference/android/arch/persistence/room/Transaction)
- [Room releases on GitHub (androidx-releases/Room)](https://github.com/androidx-releases/Room/releases)
- [Google Issue Tracker – suspend @RawQuery generic DAO bug #137878827](https://issuetracker.google.com/issues/137878827)
- [Google Issue Tracker – Room 2.8.0 minSdk bump b/380448311](https://issuetracker.google.com/issues/380448311)
- [ProAndroidDev – Room lessons from multiple-table joins](https://proandroiddev.com/room-database-lessons-learnt-from-working-with-multiple-tables-d499c9be94ce)
- [Florina Muntenescu – Room + Coroutines (Android Developers)](https://medium.com/androiddevelopers/room-coroutines-422b786dc4c5)
- [CommonsWare AndroidArch – @RawQuery and Reactive Responses](https://commonsware.com/AndroidArch/pages/chap-rxroom-006.html)
- [CommonsWare Room – Room and the Main Application Thread](https://commonsware.com/Room/pages/chap-roomthreads-001.html)
