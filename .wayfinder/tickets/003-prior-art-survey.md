# Prior Art Survey — Dynamic Query Builders for Android / Kotlin

`wayfinder:research` | status: closed | assignee: claude
parent: map.md | blocks: 004-dsl-grammar-prototype

## Question

What existing libraries or experiments tackle dynamic query building for Android Room or Kotlin-based SQL, and what design lessons — positive and negative — can room-query-beauty learn from them?

Survey targets:

1. **SQLDelight** — does it support dynamic query building or is it compile-time only?
2. **Exposed (JetBrains)** — how does its Kotlin DSL express dynamic WHERE clauses? What can we borrow?
3. **Room's own `@RawQuery` usage patterns** — any official Google samples or Architecture Components guidance on dynamic queries?
4. **Ktorm** — DSL shape, type-safety approach, operator overloading style
5. **Android-query or similar micro-libs** — any GitHub projects specifically targeting Room dynamic queries?
6. **JOOQ (JVM)** — the gold standard for type-safe SQL DSLs; what patterns are worth porting to Android?

For each: note the DSL syntax style, how column refs are expressed (string / KProperty / generated), whether it supports Flow/coroutines, and what its biggest limitation is in the Android/Room context.

## Resolution

_Researched: 2026-09-05. Sources: primary docs and source code as cited per library._

### 1. SQLDelight

**DSL syntax style — dynamic WHERE clause**

SQLDelight does not provide a runtime query builder. All SQL is written in `.sq` files at development time; the Gradle build then generates typed Kotlin functions. Dynamic filtering requires pre-authoring every combination of conditions or falling back to a parameterized SQL string passed raw through the generated `execute` API. There is no equivalent to `andWhere { }` at the call site.

A representative GitHub issue (opened 2019, closed without resolution) asked: _"Is there a possibility of executing dynamically created RAW SQL String using SQLDelight?"_ — the answer from the project is effectively "no native support." ([issue #1465](https://github.com/cashapp/sqldelight/issues/1465); [issue #555](https://github.com/cashapp/sqldelight/issues/555) on filtering also went unresolved.)

**Column ref mechanism**

Columns are not referenced in Kotlin at all at the query composition layer. They exist only inside `.sq` file SQL text. The generated Kotlin functions expose typed parameters for bind values, not typed column references.

**Flow/coroutines support**

Yes — via the `coroutines-extensions` artifact. Extension functions convert any generated query into a `Flow` that re-emits whenever the underlying table changes:

```kotlin
implementation("app.cash.sqldelight:coroutines-extensions:2.0.2")

val players: Flow<List<HockeyPlayer>> =
    playerQueries.selectAll()
        .asFlow()
        .mapToList(Dispatchers.IO)
```

Source: [SQLDelight Android coroutines docs](https://sqldelight.github.io/sqldelight/2.0.2/android_sqlite/coroutines/)

**Biggest Android/Room limitation**

SQLDelight is a full Room replacement, not a companion. It owns schema, migrations, and query execution through its own driver layer. Adopting it inside an existing Room project means migrating the entire database layer. Its design philosophy is compile-time safety above all — dynamic query composition is a deliberate non-goal, which makes it unsuitable as a runtime query DSL layered on top of Room.

---

### 2. Exposed (JetBrains)

**DSL syntax style — dynamic WHERE clause**

Exposed provides `.andWhere { }` / `.orWhere { }` extension functions on `Query`. Conditions are `Op<Boolean>` expressions composed with infix operators. A filter is added only when needed:

```kotlin
fun findWithConditionalWhere(directorName: String?, sequelId: Int?) {
    val query = StarWarsFilmsTable.selectAll()
    directorName?.let { query.andWhere { StarWarsFilmsTable.director eq it } }
    sequelId?.let { query.andWhere { StarWarsFilmsTable.sequelId eq it } }
}
```

`compoundAnd()` and `compoundOr()` collapse a `List<Op<Boolean>>` into a single expression. Source: [Exposed — Querying Data](https://www.jetbrains.com/help/exposed/dsl-querying-data.html)

**Column ref mechanism**

Columns are `Column<T>` properties declared on a `Table` object. They are typed Kotlin objects, enabling the `eq`, `like`, `greater`, etc. infix operators at compile time. No string literals appear in the DSL layer.

**Flow/coroutines support**

Not officially supported in the core library. All operations use synchronous `transaction { }` blocks. There is no `suspend` variant of `transaction` and no `Flow`-returning query API in the documented surface as of the current docs. ([Getting Started](https://www.jetbrains.com/help/exposed/getting-started-with-exposed.html) shows no async usage.)

**Biggest Android/Room limitation**

Exposed is built entirely on `exposed-jdbc`, which requires a JDBC driver and a full JVM JDBC stack. Android does not ship a JDBC driver; SQLite on Android is accessed through the `android.database.sqlite` API or Room's `SupportSQLiteOpenHelper`. Wiring Exposed to Android would require a JDBC shim (e.g., SQLDroid), which introduces its own bugs and is not officially supported. In practice Exposed cannot be used on Android as a drop-in companion to Room.

---

### 3. Ktorm

**DSL syntax style — dynamic WHERE clause**

Ktorm provides `whereWithConditions { }` for accumulating conditions at runtime. Each predicate is pushed into a mutable list with `+=`; Ktorm `AND`s them together. Single static filters use a `where { }` lambda:

```kotlin
// Static filter with operator overloading
database
    .from(Employees)
    .select(Employees.name)
    .where { (Employees.departmentId eq 1) and (Employees.name like "%vince%") }

// Dynamic multi-condition accumulation
val query = database
    .from(Employees)
    .select(Employees.name)
    .whereWithConditions {
        if (someCondition)   it += Employees.managerId.isNull()
        if (otherCondition)  it += Employees.departmentId eq 1
    }
```

Source: [Ktorm Quick Start](https://www.ktorm.org/en/quick-start.html)

**Column ref mechanism**

Columns are declared as `val` properties on a `Table<Nothing>` (or `Table<Entity>`) Kotlin object, typed as `Column<T>`. The `eq`, `like`, `isNull`, `greater` etc. operators are extension functions on `ColumnDeclaring<T>`, providing full compile-time type safety without code generation.

```kotlin
object Employees : Table<Nothing>("t_employee") {
    val id         = int("id").primaryKey()
    val name       = varchar("name")
    val salary     = long("salary")
    val departmentId = int("department_id")
}
```

Source: [Ktorm Quick Start](https://www.ktorm.org/en/quick-start.html)

**Flow/coroutines support**

The main `ktorm` library is based on blocking JDBC and provides no native `suspend` or `Flow` APIs. A separate project, `ktorm-r2dbc` ([github.com/kotlin-orm/ktorm-r2dbc](https://github.com/kotlin-orm/ktorm-r2dbc)), adds R2DBC + coroutines for JVM server use. On Android neither JDBC nor R2DBC is viable, so there is no supported coroutines integration. ([GitHub issue #375](https://github.com/kotlin-orm/ktorm/issues/375) confirmed the gap.)

**Biggest Android/Room limitation**

Same fundamental problem as Exposed: Ktorm requires JDBC. Android has no JDBC layer. Ktorm cannot talk to Android's native SQLite API or Room's `SupportSQLiteOpenHelper`. The library is designed for server-side JVM use only.

---

### 4. JOOQ

**DSL syntax style — dynamic WHERE clause**

jOOQ treats every SQL clause as a composable, nullable expression. The idiomatic dynamic-WHERE pattern starts from `noCondition()` and chains `.and()` conditionally:

```java
Condition result = noCondition();
if (request.getParameter("title") != null)
    result = result.and(BOOK.TITLE.like(request.getParameter("title")));
if (request.getParameter("author") != null)
    result = result.and(BOOK.AUTHOR_ID.eq(...));

ctx.selectFrom(BOOK).where(result).fetch();
```

Source: [jOOQ Dynamic SQL manual](https://www.jooq.org/doc/latest/manual/sql-building/dynamic-sql/)

**Column ref mechanism**

Two modes coexist:

1. **Code-generated** (recommended): Running the jOOQ code generator against a live database schema produces `Tables.BOOK`, `BOOK.TITLE`, `BOOK.AUTHOR_ID` etc. as strongly-typed `TableField<BookRecord, String>` objects. Type mismatches are compile errors.
2. **`DSL.field()`** (escape hatch): `DSL.field("column_name", SQLDataType.VARCHAR)` creates an untyped field from a plain string — useful when the schema is not known at generation time.

Source: [jOOQ Code Generation](https://www.jooq.org/doc/latest/manual/code-generation/)

**Flow/coroutines support**

jOOQ itself is a JVM library with no Kotlin coroutines integration in its core API. It can be called from coroutines via `withContext(Dispatchers.IO)`, but there is no native `Flow`-returning query API. The `jooq-kotlin` extension module adds some Kotlin convenience functions but not reactive streams.

**Biggest Android/Room limitation**

jOOQ depends on JDBC. More critically, the code-gen step requires connecting to a live relational database (or a dialect-specific schema introspection file) during the build — this is incompatible with how Android projects manage SQLite schema (Room migrations, `@Entity` annotations). Running the jOOQ Gradle plugin against an Android project's Room schema is not a documented or supported path. Additionally, jOOQ's open-source edition (Apache 2.0) covers only a subset of dialects; advanced features require a commercial licence. Community discussion (Google Groups jooq-user, 2018) confirmed that while jOOQ is usable as a query builder on Android via a JDBC shim, the JDBC shim itself (e.g., SQLDroid) has unresolved bugs and is not recommended for production.

---

### 5. Android-specific / Room dynamic query libs

**Room's own `@RawQuery` + `SupportSQLiteQuery` / `SimpleSQLiteQuery`**

Room's official escape hatch for runtime-composed SQL is the `@RawQuery` annotation (source: [androidx.room.RawQuery](https://developer.android.com/reference/androidx/room/RawQuery)). A DAO method accepts a `SupportSQLiteQuery` argument; callers pass a `SimpleSQLiteQuery` constructed from a hand-built SQL string plus a positional-parameter array:

```kotlin
@Dao
interface FooDao {
    @RawQuery
    suspend fun findWithQuery(query: SupportSQLiteQuery): List<Foo>
}

// Call site
val sql = buildString {
    append("SELECT * FROM foo WHERE 1=1")
    if (filter.name != null) append(" AND name = ?")
}
val args = listOfNotNull(filter.name).toTypedArray()
fooDao.findWithQuery(SimpleSQLiteQuery(sql, args))
```

Room's coroutines integration applies normally when the DAO method is `suspend` or returns `Flow<T>`. Source: [Android async DAO queries](https://developer.android.com/training/data-storage/room/async-queries)

The limitation: `@RawQuery` is purely string-based. Column names, table names, and operator choices are untyped `String` literals. There is no compile-time check that the column names in the constructed SQL match the actual Room entity fields.

**AniTrend / support-query-builder**

GitHub: [AniTrend/support-query-builder](https://github.com/AniTrend/support-query-builder)

A small open-source Android library that wraps `@RawQuery` with an infix Kotlin DSL:

```kotlin
builder from table where { column equal "something" } orderByDesc column
```

Columns are expressed as `String`-backed `Column` objects created with `"column_name".asColumn()`. The library includes a KSP annotation processor (`@EntitySchema`) that generates schema objects (e.g., `PetEntitySchema.breedGroup`) from Room `@Entity` classes, giving typed constant references without embedding raw strings at call sites.

Limitations: SELECT queries only (no INSERT/UPDATE/DELETE builder); no Flow API; KSP processor must be applied to every entity that needs a schema object; the generated constants are still `String`-valued at runtime (type safety is constant-reference safety, not type-parameterized).

**Other GitHub search findings**

Searching GitHub for `room dynamic query kotlin dsl` reveals no further mature libraries. The pattern is consistently either: (a) hand-rolled string builders wrapped in `SimpleSQLiteQuery`, or (b) the AniTrend approach above. No KSP-based library generates fully type-parameterized column references (analogous to jOOQ's `TableField<R, T>`) from Room `@Entity` annotations.

---

### Design Lessons for RoomQl

RoomQl is a new Kotlin DSL for dynamic Room queries that uses KSP to generate type-safe column refs from `@Entity` classes (similar to jOOQ's codegen, but KSP-driven and Room-native).

**Lesson 1 (from jOOQ — adopt the `noCondition()` seed pattern).** jOOQ's `noCondition().and(predicate)` idiom lets callers build up a condition list without null-checking or `if (first) X else X.and(Y)` branching. RoomQl should expose an equivalent — a neutral `WhereClause.empty()` or `WhereClause.all()` sentinel that can be `.and()`-ed onto without special-casing the empty case. Without it, callers have to manage a nullable accumulator, which jOOQ proved is a persistent source of bugs.

**Lesson 2 (from Ktorm — `whereWithConditions { }` over flat `.andWhere()` chaining).** Ktorm's `whereWithConditions { it += ... }` block keeps the accumulation scope lexically contained and makes the "add only if non-null" pattern idiomatic (`if (x != null) it += col eq x`). Exposed's `.andWhere()` chain is functionally equivalent but scatters the accumulation across multiple call-site lines, hurting readability. RoomQl's primary filter API should be a block form rather than a chained-method form.

**Lesson 3 (from AniTrend/support-query-builder — KSP-generated schema objects must be typed, not just named).** support-query-builder's KSP processor generates column constants that are `String`-backed; you cannot accidentally pass a `String` column ref to a condition expecting an `Int` column, but the processor does not prevent passing a text column to `greaterThan`. RoomQl's KSP step should generate `Column<T>` objects parameterized on the Kotlin type of the underlying `@Entity` field (e.g., `Column<Int>` for an `Int` field, `Column<String>` for a `String` field), so that the DSL operators — `eq`, `greaterThan`, `like` — are restricted at compile time to compatible types, following Ktorm's `ColumnDeclaring<T>` model rather than the string-constant model.
