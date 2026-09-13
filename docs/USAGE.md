# RoomQL Usage Guide — building dynamic Android Room queries in Kotlin

Every operator in the **RoomQL 1.0.0** DSL, the SQL each one generates, and the idioms for joins, `Flow`, error handling, and testing. For the pitch, installation, API reference, and limitations, see the [main README](../README.md).

RoomQL is a type-safe Kotlin DSL that builds a Room `@RawQuery` at runtime, so a search screen whose filters are chosen by the user needs neither `(:minAge IS NULL OR age >= :minAge)` string tricks nor one DAO method per filter combination. This guide assumes you have added the three artifacts — `roomql-runtime`, `roomql-runtime-android`, and `roomql-ksp-processor` — as shown in the [installation section](../README.md#installation).

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
10. [Joining tables with INNER JOIN and LEFT JOIN](#joining-tables-with-inner-join-and-left-join)
11. [Returning suspend and Flow results](#returning-suspend-and-flow-results)
12. [Converting a RoomQlQuery with .toQuery()](#converting-a-roomqlquery-with-toquery)
13. [Error handling: what build() rejects and why](#error-handling-what-build-rejects-and-why)
14. [A complete repository](#a-complete-repository)
15. [Testing generated SQL without a device](#testing-generated-sql-without-a-device)
16. [Troubleshooting](#troubleshooting)

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

## Optional filters: how null values drop out of the SQL

This is the feature RoomQL exists for. Every RoomQL operator that takes a value accepts a **nullable** one, and emits nothing at all when that value is `null` — so a filter the user left blank is simply absent from the generated SQL, with no `if` ladder and no `(:minAge IS NULL OR age >= :minAge)` trick.

```kotlin
fun searchUsers(minAge: Int?, status: String?) = query {
    from(UserEntityTable)
    where {
        UserEntityTable.age gte minAge       // dropped when minAge == null
        UserEntityTable.status eq status     // dropped when status == null
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

> ⚠️ **`null` means "skip this condition", not "match NULL".** Passing `null` widens the result set rather than narrowing it. To match SQL `NULL`, use `isNull()`, which never skips.

---

## Operator reference: every condition RoomQL generates

These operators are available inside `where { }` and `having { }`. Each one takes a nullable value and skips itself when that value is `null`, except `isNull` and `isNotNull`, which never skip.

### Comparisons

```kotlin
UserEntityTable.age eq 30          // age = ?
UserEntityTable.age notEq 30       // age != ?
UserEntityTable.age gt 18          // age > ?
UserEntityTable.age gte 18         // age >= ?
UserEntityTable.age lt 65          // age < ?
UserEntityTable.age lte 65         // age <= ?
```

### Text matching

`like` and `notLike` take the SQL pattern verbatim, so you place the wildcards. `contains` writes them for you. All three are restricted to `String` columns, so they cannot be applied to a numeric column by mistake.

```kotlin
UserEntityTable.name like "A%"        // name LIKE ?         args: ["A%"]
UserEntityTable.name notLike "A%"     // name NOT LIKE ?     args: ["A%"]
UserEntityTable.name contains "ali"   // name LIKE ?         args: ["%ali%"]
```

### Sets

```kotlin
UserEntityTable.status inList listOf("active", "pending")
// status IN (?,?)   args: ["active", "pending"]

UserEntityTable.status notInList listOf("banned")
// status NOT IN (?)  args: ["banned"]
```

`inList` and `notInList` skip when the list is `null` **or empty** — an empty list means "no filter", never "match nothing".

### Ranges

```kotlin
UserEntityTable.age.between(18, 65)     // age BETWEEN ? AND ?   args: [18, 65]
UserEntityTable.age.between(18, null)   // skipped: upper bound is null
```

`between` skips if **either** bound is `null`. For an open-ended range, use `gte` or `lte` alone — each skips independently.

### Null checks

```kotlin
UserEntityTable.status.isNull()      // status IS NULL
UserEntityTable.status.isNotNull()   // status IS NOT NULL
```

### Null-skipping summary

| Operator | Skips when |
|---|---|
| `eq`, `notEq`, `gt`, `gte`, `lt`, `lte` | value is `null` |
| `like`, `notLike`, `contains` | value is `null` |
| `inList`, `notInList` | list is `null` or empty |
| `between` | either bound is `null` |
| `isNull`, `isNotNull` | never |

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

`having { }` accepts exactly the same operators as `where { }`, including null-skipping, and requires `groupBy(...)` to be set or `build()` throws. RoomQL 1.0.0 groups by a **single** column and selects whole rows: there are no aggregate expressions (`COUNT`, `SUM`) and no multi-column `GROUP BY`. For those, a static Room `@Query` remains the right tool.

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

Every case that throws:

| Mistake | Message |
|---|---|
| No `from()` | `from() must be called before build()` |
| `limit(0)` or negative | `limit() must be a positive integer, got 0` |
| `offset()` without `limit()` | `offset() requires limit() to be set` |
| `having { }` without `groupBy()` | `having() requires groupBy() to be set` |
| `join()` after a raw-string `from(String)` | `join() requires from(EntityTable) so columns can be aliased; from(String) has no column metadata` |

These are programming errors rather than user input errors, so they are unchecked by design: a query that is wrong is wrong on every run, and will fail the first time the code path executes.

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
                UserEntityTable.age gte minAge
                UserEntityTable.status eq status
            }
            orderBy(UserEntityTable.age, SortDirection.DESC)
            limit(50)
        }
        return userDao.search(q.toQuery())
    }

    suspend fun findByStatus(status: String?): List<UserEntity> {
        val q = query {
            from(UserEntityTable)
            where { UserEntityTable.status eq status }
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
    val q = query {
        from(UserEntityTable)
        where {
            UserEntityTable.age gte 18
            UserEntityTable.status eq null
        }
    }
    assertEquals("SELECT * FROM users WHERE age >= ?", q.sql)
    assertEquals(listOf(18), q.args)
}
```

Asserting on `sql` and `args` is the fastest way to pin down null-skipping behaviour, since each filter combination is a separate one-line case. For end-to-end coverage against a **real in-memory Room database** — JOIN result mapping, `Flow` re-emission, null-skipping through the whole stack — see the runnable [`:sample`](../sample) module, a product catalogue of `products` and `brands`:

```
./gradlew :sample:testDebugUnitTest
```

To see these same queries driving a real UI — and compared side by side against the approaches RoomQL replaces — see the [demo app](../demo).

---

## Troubleshooting

### Why does my RoomQL query return more rows than expected?

A `null` filter value removes its condition from the SQL instead of matching `NULL`, so `status eq null` in RoomQL 1.0.0 produces no `WHERE` clause at all rather than `WHERE status IS NULL`. If a filter appears to be ignored, check whether the value reaching it is `null` — and use `isNull()` when you actually want to match SQL `NULL`.

### Why doesn't my Flow re-emit when the table changes?

Room cannot infer which tables a raw query reads, so a `Flow`-returning method must declare them itself: `@RawQuery(observedEntities = [UserEntity::class])`. If that list is missing or names the wrong entity, the `Flow` emits once and then goes quiet. Every table a RoomQL join touches has to appear in the list, not just the primary one.

### Why do my JOIN result fields come back unmapped or zeroed?

When joined tables share a column name, RoomQL aliases the colliding ones to `table__column` (for example `users__id`), so a result class asking for the bare name `id` matches nothing. Map those fields with `@ColumnInfo(name = "users__id")`; columns whose names are unique across the join keep their bare names.

### Why does build() throw "join() requires from(EntityTable)"?

RoomQL can only alias colliding columns if it knows what columns each table has, and the raw-string overload `from("users")` carries no such metadata. Use the generated `from(UserEntityTable)` for any query with a join — RoomQL raises `RoomQlException` here rather than silently emitting an unaliased `SELECT *` that would corrupt the mapped result.

### Why isn't the generated *Table object resolving in my code?

The `*Table` objects are generated by the RoomQL KSP processor into the **same package** as the entity, so check that `ksp(...)` (not `implementation(...)`) is applied to `roomql-ksp-processor`, that the `com.google.devtools.ksp` plugin is on the module, and that the module has been built at least once. If you set the `roomql.tableSuffix` option, the object's name ends with your suffix instead of `Table`.

### Can I see the SQL a RoomQL query will run before running it?

Yes — `query { }` returns a `RoomQlQuery` whose `sql` and `args` are ordinary readable properties, so `println(q.sql)` shows the exact statement and `q.args` the bound values in order. This works anywhere, including plain JVM unit tests, because inspecting a query needs neither Android nor a database connection.
