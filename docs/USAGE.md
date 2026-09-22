# RoomQL Usage Guide — building dynamic Android Room queries in Kotlin

This guide walks through the **RoomQL 2.0.0** DSL one task at a time. Every example shows the **Kotlin you write** and the **SQL RoomQL generates**, so you always know exactly what reaches SQLite.

It starts from an empty project: [Installation](#installation) covers everything you need to add. For the pitch, API reference, and limitations, see the [main README](../README.md).

### When you need RoomQL

A plain Room `@Query` is fixed at compile time. That is fine when only the *values* change: `WHERE (:status IS NULL OR status = :status)` already handles an optional filter.

RoomQL is for when the *shape* of the query changes at runtime:

- the user picks which column to **sort** or **group** by,
- a **join** is only needed for some screens,
- you want to **select** different columns depending on the view.

SQL can bind a value as a `?` parameter, but never a column name. So a static `@Query` either needs a hard-to-read `CASE` ladder or cannot do it at all. RoomQL lets you build these queries in Kotlin, with typed column references instead of strings. The [README comparison](../README.md#how-roomql-compares-to-the-alternatives) goes into the trade-offs.

---

## Contents

1. [Installation](#installation)
2. [How RoomQL works: the three moving parts](#how-roomql-works-the-three-moving-parts)
3. [Defining Room entities for RoomQL](#defining-room-entities-for-roomql)
4. [Declaring the @RawQuery DAO and database](#declaring-the-rawquery-dao-and-database)
5. [Your first dynamic query](#your-first-dynamic-query)
6. [Combining conditions with AND and OR](#combining-conditions-with-and-and-or)
7. [Optional filters: two operators for two intents](#optional-filters-two-operators-for-two-intents)
8. [Operator reference: every condition RoomQL generates](#operator-reference-every-condition-roomql-generates)
9. [Sorting and paginating with ORDER BY, LIMIT, and OFFSET](#sorting-and-paginating-with-order-by-limit-and-offset)
10. [Grouping rows with GROUP BY and HAVING](#grouping-rows-with-group-by-and-having)
11. [Selecting specific columns and aggregates with select](#selecting-specific-columns-and-aggregates-with-select)
12. [Joining tables with INNER JOIN and LEFT JOIN](#joining-tables-with-inner-join-and-left-join)
13. [Returning suspend and Flow results](#returning-suspend-and-flow-results)
14. [Converting a RoomQlQuery with .toQuery()](#converting-a-roomqlquery-with-toquery)
15. [Error handling: what build() rejects and why](#error-handling-what-build-rejects-and-why)
16. [A complete repository](#a-complete-repository)
17. [Testing generated SQL without a device](#testing-generated-sql-without-a-device)
18. [Runnable examples](#runnable-examples)
19. [Troubleshooting](#troubleshooting)

---

## Installation

You add **two** dependencies. RoomQL publishes three artifacts, but `runtime-android` brings `runtime` with it, so you never declare `runtime` yourself.

| You declare | What it gives you | How |
|---|---|---|
| `io.github.kotplat.roomql:runtime-android` | The `.toQuery()` bridge to Room, **plus** the `query { }` DSL from `runtime` | `implementation(...)` |
| `io.github.kotplat.roomql:ksp-processor` | Generates the `*Table` column objects at build time | `ksp(...)` |

### 1. Check the requirements

In short: Kotlin 2.0.21 or newer, minSdk 21+, and JDK 17 or newer to run the build. The [requirements table in the README](../README.md#requirements) has the full list, including which KSP and Room versions go together.

### 2. Apply the KSP plugin

RoomQL's processor runs on KSP (Kotlin Symbol Processing), the same tool Room's own compiler uses. If your module already uses Room with KSP, this step is done.

KSP versions are tied to Kotlin versions, so pick the one matching yours. The example below is for Kotlin 2.0.21.

```kotlin
// build.gradle.kts (project root)
plugins {
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}
```

```kotlin
// build.gradle.kts (your app or data module)
plugins {
    id("com.google.devtools.ksp")
}
```

### 3. Add the dependencies

With a version catalog (`gradle/libs.versions.toml`):

```toml
[versions]
roomql = "2.0.0"

[libraries]
roomql-runtime-android = { module = "io.github.kotplat.roomql:runtime-android", version.ref = "roomql" }
roomql-ksp-processor   = { module = "io.github.kotplat.roomql:ksp-processor",   version.ref = "roomql" }
```

```kotlin
// build.gradle.kts (your app or data module)
dependencies {
    implementation(libs.roomql.runtime.android)
    ksp(libs.roomql.ksp.processor)
}
```

Or without a catalog:

```kotlin
dependencies {
    implementation("io.github.kotplat.roomql:runtime-android:2.0.0")
    ksp("io.github.kotplat.roomql:ksp-processor:2.0.0")
}
```

Both artifacts are on Maven Central, so `mavenCentral()` in your repositories is all you need.

> **Watch out:** keep both on the **same version**. The generated code calls into `runtime` by name, so a mismatch shows up as an unresolved `*Table` or `*Projection` reference, not a clear version error.

**Why can't `ksp-processor` come along automatically too?** Gradle never passes a code processor through `implementation` or `api`. Every KSP-based library (Room, Moshi, Hilt) needs its own `ksp(...)` line for the same reason.

### 4. Add Room, if you haven't yet

RoomQL builds queries for Room; it doesn't replace it. If your module doesn't use Room yet, add it the usual way:

```kotlin
dependencies {
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")     // suspend and Flow support
    ksp("androidx.room:room-compiler:2.6.1")
}
```

### 5. Build once

The `*Table` objects don't exist until KSP runs. Build the module once (**Build → Make Project**, or `./gradlew assembleDebug`) and `UserEntityTable` and friends appear, ready to import.

### Only need the DSL, without Android?

For example, a pure-Kotlin module that only builds queries, or plain JVM tests of the SQL. Declare `runtime` on its own:

```kotlin
dependencies {
    implementation("io.github.kotplat.roomql:runtime:2.0.0")
}
```

---

## How RoomQL works: the three moving parts

RoomQL has three pieces, each in its own artifact. You declare two of them; `runtime` comes with `runtime-android`:

| Piece | What it does | Artifact |
|---|---|---|
| **Generated column references** | At build time, the KSP processor reads each `@Entity` and generates an `object <EntityName>Table` with one typed `Column<T>` per column. | `ksp-processor` |
| **The `query { }` builder** | You describe the query in Kotlin. It returns a `RoomQlQuery`: the SQL string plus its arguments. Pure Kotlin, no Android. | `runtime` (via `runtime-android`) |
| **The `.toQuery()` bridge** | Turns a `RoomQlQuery` into the `SupportSQLiteQuery` that Room's `@RawQuery` methods accept. | `runtime-android` |

```
@Entity ──(KSP)──▶ UserEntityTable ──(query { })──▶ RoomQlQuery ──(.toQuery())──▶ SupportSQLiteQuery ──▶ @RawQuery
```

**Why split it this way?** The builder has no Android dependency. So you can check the exact SQL in a plain JVM unit test, and only touch Android at the moment you hand the query to Room.

> **Note:** RoomQL never generates DAO code. You write the `@RawQuery` methods yourself, so you stay in control of return types.

---

## Defining Room entities for RoomQL

You don't add anything RoomQL-specific to your entities. Write them exactly as you would for Room:

```kotlin
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val age: Int,
    val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
```

Build once, and the KSP processor generates `UserEntityTable` in the **same package**. You never write this object yourself. It gives you one typed column per property:

```kotlin
UserEntityTable.age         // Column<Int>,  SQL name "age"
UserEntityTable.status      // Column<String>, SQL name "status"
UserEntityTable.createdAt   // Column<Long>, SQL name "created_at"
```

You now write `UserEntityTable.age` instead of the string `"age"`. If you rename the property, every query that uses it stops compiling. You find the problem at build time, not in production.

How your entity annotations carry over:

| In your entity | In the generated `*Table` |
|---|---|
| `@Entity(tableName = "users")` | becomes `tableName`. Without it, the class name is used. |
| `@ColumnInfo(name = "created_at")` | the Kotlin name stays `createdAt`; the SQL uses `created_at`. |
| A nullable property (`val email: String?`) | becomes a nullable `Column<String?>`. |
| `@Ignore` on a property | is skipped. It isn't a column. |

The generated name is the entity name plus `Table`. If that clashes with a name you already use, change the suffix with a KSP option:

```kotlin
ksp {
    arg("roomql.tableSuffix", "Columns")   // generates UserEntityColumns instead
}
```

---

## Declaring the @RawQuery DAO and database

A `@RawQuery` method is Room's way of running a query that is built at runtime. You declare one per return type you need:

```kotlin
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert
    fun insertAll(vararg users: UserEntity)

    @RawQuery
    fun search(query: SupportSQLiteQuery): List<UserEntity>

    @RawQuery
    suspend fun searchSuspend(query: SupportSQLiteQuery): List<UserEntity>

    @RawQuery(observedEntities = [UserEntity::class])
    fun observe(query: SupportSQLiteQuery): Flow<List<UserEntity>>
}

@Database(entities = [UserEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}
```

**Why does the `Flow` method need `observedEntities`?** A `Flow` re-emits when its tables change. Room normally works out which tables a query reads by parsing the SQL at compile time. A raw query doesn't exist until runtime, so you have to tell Room which tables to watch.

> **Watch out:** leave a table out of `observedEntities` and the `Flow` won't react when that table changes. This is a Room rule, not a RoomQL one. See [Troubleshooting](#why-doesnt-my-flow-re-emit-when-the-table-changes).

---

## Your first dynamic query

```kotlin
import com.roomql.runtime.query
import com.roomql.android.toQuery

val q = query {
    from(UserEntityTable)
    where { UserEntityTable.age gte 18 }
}

val adults: List<UserEntity> = userDao.search(q.toQuery())
```

Generated SQL:

```sql
SELECT * FROM users WHERE age >= ?
-- args: [18]
```

Three steps: build the query, convert it with `.toQuery()`, and pass it to your DAO.

Notice the `?`. RoomQL never pastes a value into the SQL text. Every value is sent to SQLite separately, as an argument. That is what makes SQL injection impossible here: user input can't change the query, because it is never part of the query text.

---

## Combining conditions with AND and OR

**Everything inside one `where { }` block is joined with AND:**

```kotlin
query {
    from(UserEntityTable)
    where {
        UserEntityTable.age gte 18
        UserEntityTable.status eq "active"
    }
}
```

```sql
SELECT * FROM users WHERE age >= ? AND status = ?
-- args: [18, "active"]
```

**Use `or { }` when any one of several conditions is enough.** RoomQL wraps the group in parentheses, so it combines correctly with the ANDs around it:

```kotlin
query {
    from(UserEntityTable)
    where {
        UserEntityTable.status eq "active"
        or {
            UserEntityTable.age lt 18
            UserEntityTable.age gt 65
        }
    }
}
```

```sql
SELECT * FROM users WHERE status = ? AND (age < ? OR age > ?)
-- args: ["active", 18, 65]
```

**You can call `where { }` more than once.** Each call adds its conditions to the same AND list; it never replaces earlier ones. That lets you use ordinary `if` statements:

```kotlin
query {
    from(UserEntityTable)
    where { UserEntityTable.status eq "active" }
    if (includeAgeFilter) {
        where { UserEntityTable.age gte 18 }
    }
}
```

If every condition inside an `or { }` is skipped (see the next section), the group disappears too. You never get an empty `()` in the SQL.

---

## Optional filters: two operators for two intents

Picture a search screen with an age field and a status dropdown. Both are optional: an empty field means "don't filter by this". This is the case RoomQL was built for.

RoomQL gives every operator two forms, and you choose by what you mean:

| You mean | Use | When the value is `null` |
|---|---|---|
| "This filter must apply" | `eq`, `gte`, `like`, … | Won't compile |
| "Apply this filter only if the user gave a value" | `eqIfNotNull`, `gteIfNotNull`, `likeIfNotNull`, … | The condition is left out of the SQL |

**The plain form is required.** It only accepts a non-null value, so a nullable variable is a compile error:

```kotlin
val status: String? = maybeStatus()
UserEntityTable.status eq status   // compile error: String? is not String
```

**The `IfNotNull` form is optional.** When the value is `null`, the condition disappears from the SQL:

```kotlin
fun searchUsers(minAge: Int?, status: String?) = query {
    from(UserEntityTable)
    where {
        UserEntityTable.age gteIfNotNull minAge       // dropped when minAge == null
        UserEntityTable.status eqIfNotNull status     // dropped when status == null
    }
}
```

One function, four different queries:

| Call | Generated SQL | args |
|---|---|---|
| `searchUsers(18, "active")` | `SELECT * FROM users WHERE age >= ? AND status = ?` | `[18, "active"]` |
| `searchUsers(18, null)` | `SELECT * FROM users WHERE age >= ?` | `[18]` |
| `searchUsers(null, "active")` | `SELECT * FROM users WHERE status = ?` | `["active"]` |
| `searchUsers(null, null)` | `SELECT * FROM users` | `[]` |

> **Watch out:** `IfNotNull` means "skip this condition". It does **not** mean "match NULL". `status eqIfNotNull null` returns *more* rows, not fewer. To find rows where a column is NULL, use `isNull()`.

### Why two operators instead of one smart one?

Suppose there were a single `eq` that accepted `null`. When it received `null`, it couldn't know what you meant:

- "The user left this field empty, so skip it", or
- "Find rows where this column is NULL".

Both would look identical in your code. Splitting the names removes the guesswork:

- `eqIfNotNull` can only mean "optional",
- `eq` can only mean "required",
- `isNull()` is the only way to say "match NULL".

A reviewer can read a `where { }` block and see which filters might disappear, without tracing where every variable came from.

There is also a SQL reason the required form rejects `null`. In SQL, any comparison with `NULL` is never true: `age = NULL` and even `age <> NULL` both match zero rows. So a required operator given `null` could never produce a useful query. RoomQL stops you from writing it.

---

## Operator reference: every condition RoomQL generates

Every operator that takes a value comes in two forms:

- **Plain** (`eq`, `gt`, …): the value must be non-null. The compiler checks this.
- **Optional** (`…IfNotNull`, or `…IfNotEmpty` for lists): skipped when the value is missing.

`isNull` and `isNotNull` take no value, so they have only one form.

### Comparisons

```kotlin
UserEntityTable.age eq 30                     // age = ?
UserEntityTable.age eqIfNotNull minAge        // age = ?, or skipped

UserEntityTable.age notEq 30                  // age != ?
UserEntityTable.age notEqIfNotNull minAge     // age != ?, or skipped

UserEntityTable.age gt 18                     // age > ?
UserEntityTable.age gtIfNotNull minAge        // age > ?, or skipped

UserEntityTable.age gte 18                    // age >= ?
UserEntityTable.age gteIfNotNull minAge       // age >= ?, or skipped

UserEntityTable.age lt 65                     // age < ?
UserEntityTable.age ltIfNotNull maxAge        // age < ?, or skipped

UserEntityTable.age lte 65                    // age <= ?
UserEntityTable.age lteIfNotNull maxAge       // age <= ?, or skipped
```

### Text matching

- `like` and `notLike` take a SQL pattern as-is, so you write the `%` wildcards yourself.
- `contains` adds the wildcards for you: `contains "ali"` searches for `%ali%`.

All three only work on `String` columns, so you can't use them on a number column by mistake.

```kotlin
UserEntityTable.name like "A%"                 // name LIKE ?         args: ["A%"]
UserEntityTable.name likeIfNotNull pattern      // name LIKE ?, or skipped

UserEntityTable.name notLike "A%"              // name NOT LIKE ?     args: ["A%"]
UserEntityTable.name notLikeIfNotNull pattern   // name NOT LIKE ?, or skipped

UserEntityTable.name contains "ali"            // name LIKE ?         args: ["%ali%"]
UserEntityTable.name containsIfNotNull query    // name LIKE ?, or skipped
```

### Sets

```kotlin
UserEntityTable.status inList listOf("active", "pending")
// status IN (?,?)   args: ["active", "pending"]

UserEntityTable.status inListIfNotEmpty selectedStatuses
// status IN (?,?), or skipped if selectedStatuses is null or empty

UserEntityTable.status notInList listOf("banned")
// status NOT IN (?)  args: ["banned"]

UserEntityTable.status notInListIfNotEmpty excludedStatuses
// status NOT IN (?), or skipped if excludedStatuses is null or empty
```

**Why "IfNotEmpty" and not "IfNotNull"?** Think of a filter screen with multi-select chips. If the user selects nothing, you get an empty list, and they mean "no filter". So an empty list has to skip, just like `null`.

**Why keep the plain `inList` at all?** Because an empty list sometimes means something real. SQLite treats the two plain forms differently when the list is empty:

| Plain form, empty list | SQL | Matches |
|---|---|---|
| `inList(emptyList())` | `IN ()` | **nothing** |
| `notInList(emptyList())` | `NOT IN ()` | **everything** |

If you need that behaviour, use the plain form. If an empty list should mean "no filter", use the `IfNotEmpty` form. Both `IfNotEmpty` forms simply skip.

### Ranges

```kotlin
UserEntityTable.age.between(18, 65)     // age BETWEEN ? AND ?   args: [18, 65]
```

`between` needs both bounds, and there is no `betweenIfNotNull`.

**Why not?** Imagine a price filter where the user fills in only "min". If an optional `between` skipped itself whenever a bound was missing, it would also throw away the min price the user *did* enter. That is worse than no shortcut.

For an open-ended range, use two optional comparisons instead:

```kotlin
UserEntityTable.price gteIfNotNull minPrice
UserEntityTable.price lteIfNotNull maxPrice
```

This handles every case correctly: both bounds, just one, or neither.

### Null checks

```kotlin
UserEntityTable.status.isNull()      // status IS NULL
UserEntityTable.status.isNotNull()   // status IS NOT NULL
```

This is how you match SQL `NULL` itself. They take no value, so there is nothing to make optional.

### Required vs. optional summary

| Operator | Required form | Optional form | Optional form skips when |
|---|---|---|---|
| Equality / comparison | `eq`, `notEq`, `gt`, `gte`, `lt`, `lte` | `eqIfNotNull`, `notEqIfNotNull`, `gtIfNotNull`, `gteIfNotNull`, `ltIfNotNull`, `lteIfNotNull` | value is `null` |
| Text matching | `like`, `notLike`, `contains` | `likeIfNotNull`, `notLikeIfNotNull`, `containsIfNotNull` | value is `null` |
| Set membership | `inList`, `notInList` | `inListIfNotEmpty`, `notInListIfNotEmpty` | list is `null` or empty |
| Range | `between` | *(none — use `gteIfNotNull` + `lteIfNotNull`)* | — |
| Null check | `isNull`, `isNotNull` | *(none — they take no value)* | never |

---

## Sorting and paginating with ORDER BY, LIMIT, and OFFSET

Call `orderBy` once for each sort key. The first call is the main sort, and each later call breaks ties in the one before it:

```kotlin
import com.roomql.runtime.SortDirection

query {
    from(UserEntityTable)
    orderBy(UserEntityTable.age, SortDirection.DESC)
    orderBy(UserEntityTable.name, SortDirection.ASC)   // chain for multi-column sort
    limit(20)
    offset(40)
}
```

```sql
SELECT * FROM users ORDER BY age DESC, name ASC LIMIT 20 OFFSET 40
```

For paging, `limit` is the page size, and `offset` is how many rows to skip. The example above is page 3 of 20 rows each.

Two rules, checked when the query is built:

- `limit(n)` must be **positive**.
- `offset(n)` needs a `limit` too. SQLite requires this, not RoomQL.

**Why does RoomQL help here?** Because the sort column is a `Column<T>` value, you can choose it at runtime from a dropdown. Plain SQL can't do that with a `?` parameter, since a column name can't be bound.

---

## Grouping rows with GROUP BY and HAVING

`GROUP BY` collapses rows that share a value into one row per group. `HAVING` then filters those groups, the way `WHERE` filters rows.

```kotlin
query {
    from(OrderEntityTable)
    groupBy(OrderEntityTable.userId)
    having { OrderEntityTable.total gt 100.0 }
}
```

```sql
SELECT * FROM orders GROUP BY userId HAVING total > ?
-- args: [100.0]
```

- `having { }` accepts the same operators as `where { }`, both required and `IfNotNull` forms.
- `having { }` needs a `groupBy(...)`. Without one, `build()` throws.

**To group by several columns, call `groupBy` again.** Each call adds a column:

```kotlin
query {
    from(OrderEntityTable)
    groupBy(OrderEntityTable.userId)
    groupBy(OrderEntityTable.status)
}
```

```sql
SELECT * FROM orders GROUP BY userId, status
```

**Aggregates** summarise a group: `count`, `countAll`, `sum`, `avg`, `min`, `max` (see [API.md](API.md)). You can use them in `having { }` and `orderBy(...)`, and return them with `select(...)`, covered in the [next section](#selecting-specific-columns-and-aggregates-with-select).

`groupBy(...)` only takes a plain column, never an aggregate. `GROUP BY COUNT(x)` has no meaning in SQL.

---

## Selecting specific columns and aggregates with select

By default, a query returns whole rows (`SELECT *`). Use `select(...)` when you want something else: a count, a total, or just a few columns.

### Returning a single value

```kotlin
query {
    from(ProductEntityTable)
    where { ProductEntityTable.category eq "electronics" }
    select(countAll())
}
```

```sql
SELECT COUNT(*) FROM products WHERE category = ?
-- args: ["electronics"]
```

A single value needs no name. Room maps it straight onto a simple return type:

```kotlin
@RawQuery fun countByCategory(q: SupportSQLiteQuery): Int
```

### Returning several named columns

When you return several columns into a data class, Room matches each column to a field **by name**. A column like `COUNT(id)` has no useful name, so you give it one with `alias`:

```kotlin
query {
    from(OrderEntityTable)
    groupBy(OrderEntityTable.customerId)
    select(
        OrderEntityTable.customerId,
        count(OrderEntityTable.id) alias "order_count",
    )
}
```

```sql
SELECT customerId, COUNT(id) AS `order_count` FROM orders GROUP BY customerId
```

A few things `alias` handles for you:

- **Reserved words work.** The name is wrapped in backticks, so `alias "order"` or `alias "group"` is fine. Room still sees the plain name `order`.
- **It only fits inside `select(...)`.** `alias` returns a `SelectItem`, not an `Expression`. So `orderBy(count(x) alias "n", …)` or `having { (x alias "n") gt 1 }` won't compile. SQL wouldn't accept them either.

### What build() rejects, and why

`select(...)` has a few traps where SQLite would quietly return a wrong value instead of failing. RoomQL turns those into a `RoomQlException` at `build()`:

| You write | Why it's a problem |
|---|---|
| A grouped query that selects a column not in `groupBy(...)` | Each group has many rows, but the result has one. SQLite takes that column's value from an arbitrary row, with no error. |
| An ungrouped query mixing an aggregate with a plain column, like `select(name, countAll())` | Same problem: one count, but many possible names. SQLite returns an arbitrary one. |
| Two items with the same name, like `select(UserEntityTable.id, OrderEntityTable.id)` | Room can only fill one field called `id`, so the other value is lost. Fix: alias all but one. |

Selecting a few plain columns without any aggregate, like `select(a, b)`, is always fine. It's an ordinary `SELECT a, b`.

### Generating the projection's aliases with @Projection

Hand-written aliases are strings, so they can drift. Rename a field in your result class, forget the matching `alias "..."`, and that field silently comes back empty. There is no compile error.

`@Projection` fixes this. Annotate the result class, and the KSP processor generates a function that builds the aliases for you:

```kotlin
import com.roomql.runtime.Projection

@Projection
data class CustomerOrderCount(
    val customerId: Int,
    @ColumnInfo(name = "order_count") val orderCount: Long,
)
// generates CustomerOrderCountProjection(customerId: Expression<Int>, orderCount: Expression<Long>): Array<SelectItem<*>>
```

The function takes one argument per field, in the same order. Spread its result into `select(...)` with `*`:

```kotlin
query {
    from(OrderEntityTable)
    groupBy(OrderEntityTable.customerId)
    select(*CustomerOrderCountProjection(OrderEntityTable.customerId, count(OrderEntityTable.id)))
}
```

Now the compiler checks the mapping for you:

- Forget `orderCount`, and it's a compile error, just like forgetting a constructor argument.
- Pass an `Expression<Int>` where `Expression<Long>` is expected, and it's a compile error too.

`@Projection` only covers this shape: a class whose fields are all constructor properties. For anything else, write `alias` by hand.

---

## Joining tables with INNER JOIN and LEFT JOIN

A join combines rows from two tables that match on some condition. Use `JoinType.INNER` to keep only rows that match on both sides, or `JoinType.LEFT` to keep every row from the first table, even without a match.

```kotlin
import com.roomql.runtime.JoinType

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val id: Int,
    val userId: Int,
    val total: Double,
    val status: String,
)

val q = query {
    from(UserEntityTable)
    join(OrderEntityTable, JoinType.INNER) {
        on { UserEntityTable.id eq OrderEntityTable.userId }
    }
}
```

To join more than two tables, call `join` again.

### How RoomQL aliases colliding join columns

`users` and `orders` both have columns called `id` and `status`. In a plain `SELECT *`, the result would contain two columns named `id`. Room would fill your field from one of them and silently drop the other.

RoomQL prevents this. When a column name appears in more than one table, it renames that column to `table__column`. Names that are unique stay as they are:

```sql
SELECT users.id AS users__id, name, age, users.status AS users__status,
       orders.id AS orders__id, userId, total, orders.status AS orders__status
FROM users INNER JOIN orders ON users.id = orders.userId
```

The same detection also applies to your conditions. In `where { }`, `having { }`, `groupBy(...)`, and `orderBy(...)`, a clashing column gets its table name in front. For example, `where { OrderEntityTable.status eq "paid" }` becomes `WHERE orders.status = ?`, not the ambiguous `WHERE status = ?`.

> **Note:** RoomQL can only detect clashes if it knows every table's columns. That information comes from the generated `*Table` objects. So a join needs `from(UserEntityTable)`, not the raw string `from("users")`. Mixing `from("users")` with `join(...)` throws a `RoomQlException` instead of quietly returning broken data.

### Mapping JOIN results to a data class

RoomQL doesn't generate result classes. You write your own, so you decide its shape. Map each field to its column with `@ColumnInfo`:

- A column whose name is **unique** keeps its plain name.
- A column whose name **clashed** uses the `table__column` name.

```kotlin
data class UserOrder(
    @ColumnInfo(name = "name")       val userName: String,    // unique → bare
    @ColumnInfo(name = "total")      val orderTotal: Double,  // unique → bare
    @ColumnInfo(name = "users__id")  val userId: Int,         // collided → aliased
    @ColumnInfo(name = "orders__id") val orderId: Int,        // collided → aliased
)

@Dao
interface OrderDao {
    @RawQuery
    fun usersWithOrders(query: SupportSQLiteQuery): List<UserOrder>
}
```

---

## Returning suspend and Flow results

You build the query the same way every time. Whether you get a blocking call, a `suspend` call, or a `Flow` depends only on how you declare the DAO method. That works because `.toQuery()` produces an ordinary `SupportSQLiteQuery`, which Room treats like any other query.

**One-shot (suspend):**

```kotlin
suspend fun activeUsers(): List<UserEntity> {
    val q = query {
        from(UserEntityTable)
        where { UserEntityTable.status eq "active" }
    }
    return userDao.searchSuspend(q.toQuery())
}
```

**Reactive (Flow):**

```kotlin
fun observeAdults(): Flow<List<UserEntity>> {
    val q = query {
        from(UserEntityTable)
        where { UserEntityTable.age gte 18 }
    }
    return userDao.observe(q.toQuery())   // re-emits whenever the users table changes
}
```

The `Flow` re-emits whenever a table listed in `@RawQuery(observedEntities = [...])` changes. A table missing from that list is one the `Flow` won't notice.

---

## Converting a RoomQlQuery with .toQuery()

`query { }` gives you a `RoomQlQuery`, a plain Kotlin value with two properties: `sql` and `args`. Room's `@RawQuery` wants a `SupportSQLiteQuery`. The `.toQuery()` extension from `com.roomql.android` converts one into the other. It's the only place RoomQL touches Android.

```kotlin
import com.roomql.android.toQuery

val rql = query { from(UserEntityTable) }   // RoomQlQuery — inspect .sql / .args in tests
val support = rql.toQuery()                 // SupportSQLiteQuery — pass to the DAO
```

**Why two types instead of one?** So you can test the exact SQL and arguments on the JVM, with no device, and cross into Android only at the DAO.

---

## Error handling: what build() rejects and why

RoomQL checks your query once, in `build()`, which `query { }` calls for you. Nothing throws while you're still adding clauses, so you can build a query step by step without errors part-way through.

If something is wrong, `build()` throws a `RoomQlException`:

```kotlin
import com.roomql.runtime.RoomQlException

try {
    query { where { UserEntityTable.age gte 18 } }   // no from()!
} catch (e: RoomQlException) {
    // "from() must be called before build()"
}
```

Every case that throws:

| Mistake | Message |
|---|---|
| No `from()` | `from() must be called before build()` |
| `limit(0)` or negative | `limit() must be a positive integer, got 0` |
| `offset()` without `limit()` | `offset() requires limit() to be set` |
| `having { }` without `groupBy()` | `having() requires groupBy() to be set` |
| `join()` after a raw-string `from(String)` | `join() requires from(EntityTable) so columns can be aliased; from(String) has no column metadata` |
| `select(...)` with a grouped bare column missing from `groupBy()` | `select(...) column(s) not in groupBy(): <names>` |
| `select(...)` mixing an aggregate with a bare column, ungrouped | `select(...) cannot mix an aggregate with a bare column unless groupBy() is set` |
| `alias()` name containing a backtick | `alias() names cannot contain a backtick: <names>` |
| `select(...)` items sharing an output name | `select(...) returns more than one column named: <names>; alias all but one` |

**Why unchecked?** These are bugs in how the query is written, not bad user input. A wrong query is wrong every time it runs, so it fails the first time you hit that code path, usually during development. You don't need a `try`/`catch` around every query.

### What about passing null to a required operator?

Normally you can't. `eq`, `gte`, and the other required operators take a non-null type, so a nullable variable won't compile.

The only way around that is from outside Kotlin's type checks: a Java caller, or an unchecked cast. In that case, Kotlin's built-in null check throws a `NullPointerException` right away. That is not a `RoomQlException`, but it still fails loudly instead of quietly matching zero rows.

Either way, the fix is the same: if a value can legitimately be missing, use the `IfNotNull` (or `IfNotEmpty`) form.

---

## A complete repository

Here is everything together: a repository with a filtered list, a `suspend` call, a `Flow`, and a join.

```kotlin
import com.roomql.android.toQuery
import com.roomql.runtime.JoinType
import com.roomql.runtime.SortDirection
import com.roomql.runtime.query
import kotlinx.coroutines.flow.Flow

class UserRepository(
    private val userDao: UserDao,
    private val orderDao: OrderDao,
) {
    fun searchUsers(minAge: Int?, status: String?): List<UserEntity> {
        val q = query {
            from(UserEntityTable)
            where {
                UserEntityTable.age gteIfNotNull minAge
                UserEntityTable.status eqIfNotNull status
            }
            orderBy(UserEntityTable.age, SortDirection.DESC)
            limit(50)
        }
        return userDao.search(q.toQuery())
    }

    suspend fun findByStatus(status: String?): List<UserEntity> {
        val q = query {
            from(UserEntityTable)
            where { UserEntityTable.status eqIfNotNull status }
        }
        return userDao.searchSuspend(q.toQuery())
    }

    fun observeAdults(): Flow<List<UserEntity>> {
        val q = query {
            from(UserEntityTable)
            where { UserEntityTable.age gte 18 }
        }
        return userDao.observe(q.toQuery())
    }

    fun usersWithOrders(): List<UserOrder> {
        val q = query {
            from(UserEntityTable)
            join(OrderEntityTable, JoinType.INNER) {
                on { UserEntityTable.id eq OrderEntityTable.userId }
            }
        }
        return orderDao.usersWithOrders(q.toQuery())
    }
}
```

---

## Testing generated SQL without a device

`query { }` returns a plain `RoomQlQuery`, so you can check the SQL and arguments in an ordinary JUnit test. You don't need an emulator, Robolectric, or a database:

```kotlin
@Test
fun `null status is omitted`() {
    val status: String? = null
    val q = query {
        from(UserEntityTable)
        where {
            UserEntityTable.age gte 18
            UserEntityTable.status eqIfNotNull status
        }
    }
    assertEquals("SELECT * FROM users WHERE age >= ?", q.sql)
    assertEquals(listOf(18), q.args)
}
```

This is the quickest way to cover optional filters. Each combination of present and missing values is one short test. To test against a real in-memory Room database as well, see the runnable examples below.

---

## Runnable examples

Three examples ship with RoomQL. Pick the one that matches where you are.

**Just starting? → [`MinimalExample.kt`](../sample/src/main/kotlin/com/roomql/sample/MinimalExample.kt).** The smallest complete setup, in one file: an entity, a `@RawQuery` DAO, a database, and one query with two optional filters. It fits on one screen and is written to be copied: rename the entity and you have a working dynamic query. [Its test](../sample/src/test/kotlin/com/roomql/sample/MinimalExampleTest.kt) runs it against a real database, so you know the copy works.

**Want to see the full stack? → [`:sample`](../sample).** A product catalogue with `products` and `brands` tables, DAOs, a repository, and Robolectric tests that run generated `*Table` → `query { }` → `.toQuery()` → in-memory Room. It covers what only shows up end to end: optional filters through the whole stack, join results mapped through `table__column` names, runtime sorting and paging, and `Flow` re-emission.

```
./gradlew :sample:testDebugUnitTest
```

**Want to compare approaches? → [`demo/`](../demo).** A Compose app whose main screen runs the same four-filter search four different ways:

- RoomQL,
- `(:x IS NULL OR col = :x)`,
- 16 separate DAO methods,
- hand-built string concatenation.

You can switch between them at runtime and see the SQL each one produces. A test checks that all four return the same rows for all 16 filter combinations, so the comparison is verified, not just claimed. The demo is a separate Gradle build that uses RoomQL's published artifacts, exactly as your app would:

```
./gradlew -p demo assembleDebug
```

---

## Troubleshooting

### Why does my RoomQL query return more rows than expected?

Check whether a value reaching an `IfNotNull` operator is `null`. When it is, the condition is removed from the SQL entirely. So `status eqIfNotNull null` produces no `WHERE status ...` at all; it does not become `WHERE status IS NULL`.

If you actually want rows where the column is NULL, use `isNull()`. The plain `eq` can't cause this problem, because it doesn't compile with a nullable value.

### Why doesn't my Flow re-emit when the table changes?

Room can't tell which tables a raw query reads, so you have to list them: `@RawQuery(observedEntities = [UserEntity::class])`. If the list is missing or names the wrong entity, the `Flow` emits once and then stays silent.

For a join, list **every** table the query touches, not just the one in `from(...)`.

### Why do my JOIN result fields come back unmapped or zeroed?

When joined tables share a column name, RoomQL renames the clashing columns to `table__column`, for example `users__id`. A field looking for plain `id` finds nothing.

Map those fields with `@ColumnInfo(name = "users__id")`. Columns with names unique across the join keep their plain names.

### Why does build() throw "join() requires from(EntityTable)"?

To rename clashing columns, RoomQL needs to know every column of every table. The raw string `from("users")` doesn't carry that information.

Use the generated object instead: `from(UserEntityTable)`. RoomQL throws here on purpose, because the alternative is a `SELECT *` that silently maps the wrong values.

### Why isn't the generated *Table object resolving in my code?

The KSP processor generates `*Table` objects into the **same package** as the entity. Check that:

- the processor is added with `ksp(...)`, not `implementation(...)`,
- the `com.google.devtools.ksp` plugin is applied to the module,
- the module has been built at least once.

If you set the `roomql.tableSuffix` option, the object's name ends with your suffix instead of `Table`.

### Can I see the SQL a RoomQL query will run before running it?

Yes. `query { }` returns a `RoomQlQuery` with two readable properties. `println(q.sql)` prints the exact statement, and `q.args` holds the values in order. This works anywhere, including plain JVM unit tests, because inspecting a query needs neither Android nor a database.
