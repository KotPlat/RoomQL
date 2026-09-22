# RoomQL Usage Guide — building dynamic Android Room queries in Kotlin

Every operator in the **RoomQL 2.0.0** DSL, the SQL each one generates, and the idioms for joins, `Flow`, error handling, and testing. For the pitch, installation, API reference, and limitations, see the [main README](../README.md).

RoomQL is a type-safe Kotlin DSL that builds a Room `@RawQuery` at runtime. Optional filter *values* have a fine static answer already (`(:x IS NULL OR col = :x)` on a plain `@Query`); RoomQL's actual job is queries whose *structure* also varies — which column to sort or group by, whether a join is present, which columns to project — with a typed `Column<T>` in place of a `CASE` ladder or a bare string. See the [main README](../README.md#how-roomql-compares-to-the-alternatives) for the full comparison, including which of those are merely awkward in static SQL and which are genuinely impossible. This guide assumes you have added the three artifacts — `runtime`, `runtime-android`, and `ksp-processor` (all under `io.github.kotplat.roomql`) — as shown in the [installation section](../README.md#installation).

Every example shows the **Kotlin** you write and the **SQL** RoomQL generates, so you can see exactly what reaches SQLite.

---

## Contents

1. [How RoomQL works: the three moving parts](#how-roomql-works-the-three-moving-parts)
2. [Defining Room entities for RoomQL](#defining-room-entities-for-roomql)
3. [Declaring the @RawQuery DAO and database](#declaring-the-rawquery-dao-and-database)
4. [Your first dynamic query](#your-first-dynamic-query)
5. [Combining conditions with AND and OR](#combining-conditions-with-and-and-or)
6. [Optional filters: how null values drop out of the SQL](#optional-filters-how-null-values-drop-out-of-the-sql)
7. [Operator reference: every condition RoomQL generates](#operator-reference-every-condition-roomql-generates)
8. [Sorting and paginating with ORDER BY, LIMIT, and OFFSET](#sorting-and-paginating-with-order-by-limit-and-offset)
9. [Grouping rows with GROUP BY and HAVING](#grouping-rows-with-group-by-and-having)
10. [Selecting specific columns and aggregates with select](#selecting-specific-columns-and-aggregates-with-select)
11. [Joining tables with INNER JOIN and LEFT JOIN](#joining-tables-with-inner-join-and-left-join)
12. [Returning suspend and Flow results](#returning-suspend-and-flow-results)
13. [Converting a RoomQlQuery with .toQuery()](#converting-a-roomqlquery-with-toquery)
14. [Error handling: what build() rejects and why](#error-handling-what-build-rejects-and-why)
15. [A complete repository](#a-complete-repository)
16. [Testing generated SQL without a device](#testing-generated-sql-without-a-device)
17. [Runnable examples](#runnable-examples)
18. [Troubleshooting](#troubleshooting)

---

## How RoomQL works: the three moving parts

RoomQL has exactly three pieces, one per published artifact:

1. **Generated column references.** For each Room `@Entity`, the KSP processor generates an `object <EntityName>Table` holding a typed `Column<T>` for every column.
2. **The `query { }` builder.** A pure-Kotlin DSL that turns those references into a `RoomQlQuery` — a SQL string plus positional args. No Android involved, so it is unit-testable on the JVM.
3. **The `.toQuery()` bridge.** Adapts a `RoomQlQuery` into the `SupportSQLiteQuery` that Room's `@RawQuery` methods accept.

```
@Entity ──(KSP)──▶ UserEntityTable ──(query { })──▶ RoomQlQuery ──(.toQuery())──▶ SupportSQLiteQuery ──▶ @RawQuery
```

You always write Room's `@RawQuery` methods yourself — RoomQL never generates DAO code.

---

## Defining Room entities for RoomQL

Annotate entities exactly as you would for Room; there is nothing RoomQL-specific to add. The RoomQL KSP processor reads the `@Entity` classes you already have and generates the column references from them.

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

The processor generates, in the **same package**:

```kotlin
object UserEntityTable : EntityTable {
    override val tableName = "users"
    override val allColumnNames = listOf("id", "name", "age", "status", "created_at")
    val id: Column<Int> = Column("id", "users")
    val name: Column<String> = Column("name", "users")
    val age: Column<Int> = Column("age", "users")
    val status: Column<String> = Column("status", "users")
    val createdAt: Column<Long> = Column("created_at", "users")
}
```

How entity annotations map to the generated object:

| In your entity | Effect on the generated `*Table` |
|---|---|
| `@Entity(tableName = "users")` | becomes `tableName`. Without it, the class name is used. |
| `@ColumnInfo(name = "created_at")` | the property keeps its Kotlin name (`createdAt`); the SQL uses `created_at`. |
| A nullable property (`val email: String?`) | produces a nullable `Column<String?>`. |
| `@Ignore` on a property | skipped entirely — it is not a column. |

The generated object's name is the entity class name plus a suffix, `Table` by default. Change it with the `roomql.tableSuffix` KSP option if that collides with a name you already use:

```kotlin
ksp {
    arg("roomql.tableSuffix", "Columns")   // generates UserEntityColumns instead
}
```

---

## Declaring the @RawQuery DAO and database

RoomQL builds the query but never the DAO, so you declare `@RawQuery` methods yourself and keep full control of the return types. A method that returns `Flow` additionally needs `observedEntities`, because Room cannot inspect a raw query to learn which tables it reads.

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

Omit an entity from `observedEntities` and the `Flow` will not re-emit when that table changes. This is a Room requirement, not a RoomQL one — see [Troubleshooting](#why-doesnt-my-flow-re-emit-when-the-table-changes).

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

Values are **always** bound as positional `?` placeholders and passed to SQLite as an argument list — RoomQL never interpolates a value into the SQL text, so there is no injection surface.

---

## Combining conditions with AND and OR

Conditions inside a single `where { }` block are combined with **AND**:

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

Use `or { }` to group alternatives. RoomQL wraps the OR-group in parentheses and combines it with the surrounding ANDs:

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

Calling `where { }` more than once on the same builder merges the blocks into one AND-combined set, which lets you add conditions from ordinary Kotlin control flow:

```kotlin
query {
    from(UserEntityTable)
    where { UserEntityTable.status eq "active" }
    if (includeAgeFilter) {
        where { UserEntityTable.age gte 18 }
    }
}
```

An `or { }` block whose conditions all skip contributes nothing — no empty `()` reaches the SQL.

---

## Optional filters: two operators for two intents

This is the feature RoomQL exists for, and it works through a deliberate split rather than one operator that does both jobs.

**The plain operator is required.** `eq`, `gte`, `like`, and the rest take a non-nullable value. Pass a nullable Kotlin variable and it will not compile:

```kotlin
val status: String? = maybeStatus()
UserEntityTable.status eq status   // compile error: String? is not String
```

**The `IfNotNull` operator is optional.** It takes the nullable value and skips the condition — leaves it out of the SQL entirely — when that value is `null`:

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

**Why two operators rather than one that infers your intent from the value's type.** A single `eq` accepting `T?` cannot tell "this value happens to be null right now" from "I want to match SQL `NULL`" — both compile to the same call. Splitting the name makes the ambiguity impossible to write: `eqIfNotNull` can only mean "optional", `eq` can only mean "required", and `isNull()` is the only spelling of "match `NULL`". Reading a `where { }` block tells you which conditions can disappear without tracing the nullability of every variable at the call site.

> ⚠️ **`IfNotNull` means "skip this condition", not "match NULL".** `status eqIfNotNull null` widens the result set rather than narrowing it — it does not become `status IS NULL`. Use `isNull()` for that.

Every SQL comparison against `NULL` is false, including ones you might expect to be true — `age <> NULL` matches nothing, the same as `age = NULL`. A required operator can therefore never usefully accept `null`: there is no query it could produce that means anything, so RoomQL does not let you write one.

---

## Operator reference: every condition RoomQL generates

Every value-taking operator below has two forms. The plain form is required — its value must be non-null, checked at compile time. The `IfNotNull` (or `IfNotEmpty`, for lists) form is optional and skips itself when the value is absent. `isNull` and `isNotNull` are the exception: they take no value and never skip.

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

`like` and `notLike` take the SQL pattern verbatim, so you place the wildcards. `contains` writes them for you. All are restricted to `String` columns, so they cannot be applied to a numeric column by mistake.

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

The optional forms are named for **emptiness**, not nullability, because an empty list has to skip too — a multi-select chip screen with nothing chosen means "no filter", not "match nothing". This is also why they are not simply `inList` accepting a nullable list: the required `inList` renders an empty list as `IN ()`, which SQLite defines as matching **nothing** — a real meaning some callers want, and one that skipping would take away from them. `notInList` is the asymmetric case to know: `NOT IN ()` matches **everything**, so the two required forms do not mirror each other on an empty list even though their `IfNotEmpty` counterparts behave the same way (skip).

### Ranges

```kotlin
UserEntityTable.age.between(18, 65)     // age BETWEEN ? AND ?   args: [18, 65]
```

Both bounds are required, and there is no `betweenIfNotNull`. A range missing one bound is not a range — skipping the whole condition would silently drop the bound you did supply, which is worse than requiring both. Compose an open-ended range from the two comparisons instead:

```kotlin
UserEntityTable.price gteIfNotNull minPrice
UserEntityTable.price lteIfNotNull maxPrice
```

This applies whichever bound is present, both, one, or neither, with no hidden case.

### Null checks

```kotlin
UserEntityTable.status.isNull()      // status IS NULL
UserEntityTable.status.isNotNull()   // status IS NOT NULL
```

These are how you match SQL `NULL` itself. They take no argument, so there is nothing to make optional.

### Required vs. optional summary

| Operator | Required form | Optional form | Optional form skips when |
|---|---|---|---|
| Equality / comparison | `eq`, `notEq`, `gt`, `gte`, `lt`, `lte` | `eqIfNotNull`, `notEqIfNotNull`, `gtIfNotNull`, `gteIfNotNull`, `ltIfNotNull`, `lteIfNotNull` | value is `null` |
| Text matching | `like`, `notLike`, `contains` | `likeIfNotNull`, `notLikeIfNotNull`, `containsIfNotNull` | value is `null` |
| Set membership | `inList`, `notInList` | `inListIfNotEmpty`, `notInListIfNotEmpty` | list is `null` or empty |
| Range | `between` | *(none — compose from `gteIfNotNull` + `lteIfNotNull`)* | — |
| Null check | `isNull`, `isNotNull` | *(none — these already express optionality)* | never |

---

## Sorting and paginating with ORDER BY, LIMIT, and OFFSET

Call `orderBy` once per sort key — repeated calls build a multi-column `ORDER BY` in the order you wrote them.

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

Two rules, both enforced when the query is built:

- `limit(n)` must be **positive**.
- `offset(n)` requires a `limit` to be set — a SQLite restriction, not a RoomQL one.

---

## Grouping rows with GROUP BY and HAVING

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

`having { }` accepts exactly the same operators as `where { }` — required and `IfNotNull` forms both — and requires `groupBy(...)` to be set or `build()` throws. Call `groupBy(...)` repeatedly for a multi-column `GROUP BY`:

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

`having { }` and `orderBy(...)` accept aggregate expressions like `count(...)` and `sum(...)` (see [API.md](API.md)). Project an aggregate's value into the result with `select(...)` — see [Selecting specific columns and aggregates](#selecting-specific-columns-and-aggregates-with-select) below. `groupBy(...)` itself stays `Column<T>`-only: `GROUP BY COUNT(x)` is meaningless SQL.

---

## Selecting specific columns and aggregates with select

Left out, `build()` behaves exactly as v1: `SELECT *`, with `JOIN`-collision columns aliased automatically. `select(...)` replaces that with an explicit, typed projection:

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

A single unaliased expression needs no name at all — Room binds it straight to a scalar return type (`Int`, `Long`, `Double`, …) exactly like any other query:

```kotlin
@RawQuery fun countByCategory(q: SupportSQLiteQuery): Int
```

A multi-column projection names each output with the `alias` infix:

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
SELECT customerId, COUNT(id) AS order_count FROM orders GROUP BY customerId
```

`build()` rejects two shapes that would otherwise let SQLite pick an arbitrary row's value silently:

- A **grouped** query (`groupBy(...)` set) whose projection has a bare column missing from `GROUP BY`.
- An **ungrouped** query mixing an aggregate with a bare column — the same hole, without an explicit `GROUP BY` to name.

An ungrouped, aggregate-free multi-column projection (`select(a, b)`) is just an ordinary `SELECT a, b` and passes through uncaught.

### Generating the projection's aliases with @Projection

Hand-written `alias` calls are easy to get out of sync with the result class they feed: rename a field, forget to update the string, and the mismatch surfaces as a silently unmapped column, not a compile error. Annotate the result class with `@Projection` instead, and RoomQL's KSP processor generates a typed factory function alongside it:

```kotlin
import com.roomql.runtime.Projection

@Projection
data class CustomerOrderCount(
    val customerId: Int,
    @ColumnInfo(name = "order_count") val orderCount: Long,
)
// generates CustomerOrderCountProjection(customerId: Expression<Int>, orderCount: Expression<Long>): Array<Expression<*>>
```

Spread the generated function's result into `select(...)`:

```kotlin
query {
    from(OrderEntityTable)
    groupBy(OrderEntityTable.customerId)
    select(*CustomerOrderCountProjection(OrderEntityTable.customerId, count(OrderEntityTable.id)))
}
```

Leaving out `orderCount` — or passing an `Expression<Int>` where `Expression<Long>` is expected — is a Kotlin compile error at that call, the same as forgetting a constructor argument. `@Projection` only covers this constructor-property shape; a result class KSP can't model that way still uses the plain `alias` infix.

---

## Joining tables with INNER JOIN and LEFT JOIN

Join with `join(table, type) { on { ... } }`, using `JoinType.INNER` or `JoinType.LEFT`. Chain multiple `join` calls for more than two tables.

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

### How RoomQL aliases colliding join columns

`users` and `orders` both have an `id` and a `status`. To stop a raw cursor from silently overwriting one with the other, RoomQL aliases the **colliding** columns as `table__column` and leaves unique ones bare:

```sql
SELECT users.id AS users__id, name, age, users.status AS users__status,
       orders.id AS orders__id, userId, total, orders.status AS orders__status
FROM users INNER JOIN orders ON users.id = orders.userId
```

The same collision detection qualifies references in `where { }`, `having { }`, `groupBy(...)`, and `orderBy(...)` with `table.column` whenever the column name collides — a unique name stays bare. On the query above, `where { OrderEntityTable.status eq "paid" }` renders `WHERE orders.status = ?`, not the ambiguous `WHERE status = ?`.

> Aliasing needs column metadata, so it only happens when the primary table is a generated `*Table` (`from(UserEntityTable)`). The raw-string overload `from("users")` carries none, so combining it with `join(...)` throws `RoomQlException` at `build()` rather than silently emitting an unaliased `SELECT *`.

### Mapping JOIN results to a data class

RoomQL does not generate result types — you supply your own, which is what lets you control its shape. Map colliding columns with `@ColumnInfo(name = "table__column")`; unique columns map by their bare name.

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

A RoomQL query is the same in all three cases — blocking, `suspend`, and `Flow` are decided entirely by the DAO method's return type, because `.toQuery()` produces an ordinary `SupportSQLiteQuery` that Room treats like any other.

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

The `Flow` re-emits on every change to the tables listed in `@RawQuery(observedEntities = [...])`. A table missing from that list is a table the `Flow` will not react to.

---

## Converting a RoomQlQuery with .toQuery()

`query { }` returns a `RoomQlQuery`, a pure-JVM value holding `sql` and `args`; Room's `@RawQuery` wants a `SupportSQLiteQuery`. The `.toQuery()` extension from `com.roomql.android` converts one to the other, and is the single point where RoomQL touches Android.

```kotlin
import com.roomql.android.toQuery

val rql = query { from(UserEntityTable) }   // RoomQlQuery — inspect .sql / .args in tests
val support = rql.toQuery()                 // SupportSQLiteQuery — pass to the DAO
```

Keeping the two types apart is deliberate: it lets you assert on the exact SQL and args on the JVM with no device, and cross into Android only at the DAO boundary.

---

## Error handling: what build() rejects and why

RoomQL defers all validation to `build()` — which `query { }` calls for you — and signals every invalid query with `RoomQlException`, a `RuntimeException`. Nothing throws while you are still configuring the builder, so a half-built query never blows up mid-DSL.

```kotlin
import com.roomql.runtime.RoomQlException

try {
    query { where { UserEntityTable.age gte 18 } }   // no from()!
} catch (e: RoomQlException) {
    // "from() must be called before build()"
}
```

Every case that throws `RoomQlException`:

| Mistake | Message |
|---|---|
| No `from()` | `from() must be called before build()` |
| `limit(0)` or negative | `limit() must be a positive integer, got 0` |
| `offset()` without `limit()` | `offset() requires limit() to be set` |
| `having { }` without `groupBy()` | `having() requires groupBy() to be set` |
| `join()` after a raw-string `from(String)` | `join() requires from(EntityTable) so columns can be aliased; from(String) has no column metadata` |
| `select(...)` with a grouped bare column missing from `groupBy()` | `select { } column(s) not in groupBy(): <names>` |
| `select(...)` mixing an aggregate with a bare column, ungrouped | `select { } cannot mix an aggregate with a bare column unless groupBy() is set` |

These are programming errors rather than user input errors, so they are unchecked by design: a query that is wrong is wrong on every run, and will fail the first time the code path executes.

**A required operator given `null` fails differently.** `eq`, `gte`, and the other required operators take a non-null value, so an ordinary nullable Kotlin variable will not compile against them at all — that mistake never reaches you at runtime. The one path around the type system is a `null` crossing an erased generic boundary: a Java caller, or an unchecked cast. RoomQL's own compiled bytecode carries Kotlin's standard non-null parameter check for that case, so it still fails immediately — a `NullPointerException`, not a `RoomQlException` — rather than silently binding `NULL` and matching zero rows. If you hit this, the fix is the same either way: use the `IfNotNull` (or `IfNotEmpty`) form for a value that can legitimately be absent.

---

## A complete repository

Putting it together — a repository exposing list, suspend, reactive, and JOIN queries:

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

Because `query { }` returns a plain `RoomQlQuery` from a pure-JVM module, you can assert on the generated SQL and arguments in an ordinary JUnit test — no emulator, no Robolectric, no database:

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

Asserting on `sql` and `args` is the fastest way to pin down null-skipping behaviour, since each filter combination is a separate one-line case. For end-to-end coverage against a real in-memory Room database, see the runnable examples below.

---

## Runnable examples

Three ship with RoomQL, aimed at different moments.

**[`MinimalExample.kt`](../sample/src/main/kotlin/com/roomql/sample/MinimalExample.kt) — start here.** The smallest complete setup in one annotated file: an entity, a `@RawQuery` DAO, a database, and a single query with two optional filters. Readable in one screen and written to be copied — rename the entity and you have a working dynamic query. [Its test](../sample/src/test/kotlin/com/roomql/sample/MinimalExampleTest.kt) drives it against a real database, so the copy-paste path is known to run.

**[`:sample`](../sample) — the full stack.** A product catalogue of `products` and `brands` with `@Entity` classes, a `@Database`, manual `@RawQuery` DAOs, a repository, and Robolectric tests exercising generated `*Table` → `query { }` → `.toQuery()` → in-memory Room. Covers the behaviour that only shows up end to end: null-skipping through the whole stack, JOIN result mapping through the `table__column` aliases, runtime sort and paging, and `Flow` re-emission.

```
./gradlew :sample:testDebugUnitTest
```

**[`demo/`](../demo) — the same queries driving a UI.** A Compose app whose flagship screen implements one four-filter search four ways — RoomQL, `(:x IS NULL OR col = :x)`, 16 overloaded DAO methods, and hand-built string concatenation — switching between them at runtime while showing the SQL each produces. A test asserts all four return identical rows across all 16 filter combinations, so the comparison is verified rather than claimed. It is a standalone Gradle build consuming RoomQL through its published coordinates, exactly as your app would:

```
./gradlew -p demo assembleDebug
```

---

## Troubleshooting

### Why does my RoomQL query return more rows than expected?

An `IfNotNull` filter value removes its condition from the SQL instead of matching `NULL`, so `status eqIfNotNull null` produces no `WHERE` clause at all rather than `WHERE status IS NULL`. If a filter appears to be ignored, check whether the value reaching an `IfNotNull` operator is `null` — and use `isNull()` when you actually want to match SQL `NULL`. The plain `eq` cannot cause this: it does not compile against a nullable value in the first place.

### Why doesn't my Flow re-emit when the table changes?

Room cannot infer which tables a raw query reads, so a `Flow`-returning method must declare them itself: `@RawQuery(observedEntities = [UserEntity::class])`. If that list is missing or names the wrong entity, the `Flow` emits once and then goes quiet. Every table a RoomQL join touches has to appear in the list, not just the primary one.

### Why do my JOIN result fields come back unmapped or zeroed?

When joined tables share a column name, RoomQL aliases the colliding ones to `table__column` (for example `users__id`), so a result class asking for the bare name `id` matches nothing. Map those fields with `@ColumnInfo(name = "users__id")`; columns whose names are unique across the join keep their bare names.

### Why does build() throw "join() requires from(EntityTable)"?

RoomQL can only alias colliding columns if it knows what columns each table has, and the raw-string overload `from("users")` carries no such metadata. Use the generated `from(UserEntityTable)` for any query with a join — RoomQL raises `RoomQlException` here rather than silently emitting an unaliased `SELECT *` that would corrupt the mapped result.

### Why isn't the generated *Table object resolving in my code?

The `*Table` objects are generated by the RoomQL KSP processor into the **same package** as the entity, so check that `ksp(...)` (not `implementation(...)`) is applied to `ksp-processor`, that the `com.google.devtools.ksp` plugin is on the module, and that the module has been built at least once. If you set the `roomql.tableSuffix` option, the object's name ends with your suffix instead of `Table`.

### Can I see the SQL a RoomQL query will run before running it?

Yes — `query { }` returns a `RoomQlQuery` whose `sql` and `args` are ordinary readable properties, so `println(q.sql)` shows the exact statement and `q.args` the bound values in order. This works anywhere, including plain JVM unit tests, because inspecting a query needs neither Android nor a database connection.
