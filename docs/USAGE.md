# RoomQL — Usage Guide

A detailed, example-driven walkthrough of the RoomQL DSL. For the high-level pitch, installation, and pitfalls see the [main README](../README.md). This guide assumes you've added the three dependencies (`roomql-runtime`, `roomql-runtime-android`, `roomql-ksp-processor`).

Every example shows the **Kotlin** you write and the **SQL** RoomQL generates, so you can see exactly what reaches SQLite.

---

## Table of contents

1. [The mental model](#1-the-mental-model)
2. [Define entities](#2-define-entities)
3. [Wire up the DAO and database](#3-wire-up-the-dao-and-database)
4. [Your first query](#4-your-first-query)
5. [WHERE, AND, OR](#5-where-and-or)
6. [Nullable filters (the killer feature)](#6-nullable-filters-the-killer-feature)
7. [All operators](#7-all-operators)
8. [Ordering and pagination](#8-ordering-and-pagination)
9. [Grouping and aggregates](#9-grouping-and-aggregates)
10. [JOINs](#10-joins)
11. [Suspend and Flow](#11-suspend-and-flow)
12. [The `.toQuery()` bridge](#12-the-toquery-bridge)
13. [Error handling](#13-error-handling)
14. [A complete repository](#14-a-complete-repository)
15. [Testing your queries](#15-testing-your-queries)

---

## 1. The mental model

RoomQL has exactly three moving parts:

1. **Generated column refs.** For each `@Entity`, the KSP processor generates an `object <EntityName>Table` holding a typed `Column<T>` for every column.
2. **The `query { }` builder.** A pure-Kotlin DSL that turns those refs into a `RoomQlQuery` (a SQL string + positional args). No Android involved — it's unit-testable on the JVM.
3. **The `.toQuery()` bridge.** Adapts a `RoomQlQuery` into the `SupportSQLiteQuery` that Room's `@RawQuery` methods accept.

```
@Entity ──(KSP)──▶ UserEntityTable ──(query { })──▶ RoomQlQuery ──(.toQuery())──▶ SupportSQLiteQuery ──▶ @RawQuery
```

You always write Room's `@RawQuery` methods yourself (this is "Mode B"). RoomQL never generates DAO code.

---

## 2. Define entities

Annotate entities exactly as you would for Room. Nothing RoomQL-specific here.

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

The KSP processor generates, in the **same package**:

```kotlin
object UserEntityTable : TableColumns {
    override val tableName = "users"
    override val allColumnNames = listOf("id", "name", "age", "status", "created_at")
    val id: Column<Int>
    val name: Column<String>
    val age: Column<Int>
    val status: Column<String>
    val createdAt: Column<Long>   // ← uses the @ColumnInfo name "created_at" in SQL
}
```

Notes:
- `@Entity(tableName = "...")` → the object's `tableName`. Without it, the class name is used.
- `@ColumnInfo(name = "...")` → the column's SQL name. The Kotlin property keeps its name (`createdAt`), but the generated SQL uses `created_at`.
- Nullable properties produce nullable `Column<T?>`.

---

## 3. Wire up the DAO and database

RoomQL uses Room's `@RawQuery`. You own these declarations.

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

> **Why `observedEntities` on the Flow method?** Room can't inspect a raw query to learn which tables it reads, so for a `Flow` you must tell it which entities to watch for changes. See [pitfalls](../README.md#pitfalls--limitations).

---

## 4. Your first query

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

Arguments are **always** bound as positional `?` placeholders — never string-interpolated — so there's no SQL-injection surface.

---

## 5. WHERE, AND, OR

Conditions inside a single `where { }` are combined with **AND** by default:

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

Use `or { }` to group alternatives. The OR-group is wrapped in parentheses and combined with the surrounding ANDs:

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

Multiple `where { }` calls on the same builder merge into one AND-combined set — so you can add conditions conditionally:

```kotlin
query {
    from(UserEntityTable)
    where { UserEntityTable.status eq "active" }
    if (includeAgeFilter) {
        where { UserEntityTable.age gte 18 }
    }
}
```

---

## 6. Nullable filters (the killer feature)

Any operator that takes a value **skips itself when the value is `null`**. This is what removes the `if (x != null)` ladders you'd write with raw SQL.

```kotlin
fun searchUsers(minAge: Int?, status: String?) = query {
    from(UserEntityTable)
    where {
        UserEntityTable.age gte minAge       // dropped when minAge == null
        UserEntityTable.status eq status     // dropped when status == null
    }
}
```

| Call | Generated SQL | args |
|---|---|---|
| `searchUsers(18, "active")` | `SELECT * FROM users WHERE age >= ? AND status = ?` | `[18, "active"]` |
| `searchUsers(18, null)` | `SELECT * FROM users WHERE age >= ?` | `[18]` |
| `searchUsers(null, "active")` | `SELECT * FROM users WHERE status = ?` | `["active"]` |
| `searchUsers(null, null)` | `SELECT * FROM users` | `[]` |

> ⚠️ **`null` means "skip this condition", not "match NULL".** If you want `WHERE status IS NULL`, use `isNull()` (below) — that operator never skips.

---

## 7. All operators

Available inside `where { }` and `having { }`:

### Comparisons

```kotlin
UserEntityTable.age eq 30          // age = ?
UserEntityTable.age notEq 30       // age != ?
UserEntityTable.age gt 18          // age > ?
UserEntityTable.age gte 18         // age >= ?
UserEntityTable.age lt 65          // age < ?
UserEntityTable.age lte 65         // age <= ?
```

### Text matching (you supply the SQL `LIKE` pattern)

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

`inList` / `notInList` skip when the list is `null` **or empty**.

### Ranges

```kotlin
UserEntityTable.age.between(18, 65)     // age BETWEEN ? AND ?   args: [18, 65]
UserEntityTable.age.between(18, null)   // skipped: upper bound is null
```

`between` skips if **either** bound is `null`.

### Null checks (never skipped)

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

## 8. Ordering and pagination

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

Rules (enforced at `build()`):
- `limit(n)` must be **positive**.
- `offset(n)` requires a `limit` to be set.

---

## 9. Grouping and aggregates

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

Rules:
- `having { }` requires `groupBy(...)` to be set, or `build()` throws.
- `having { }` accepts the same operators as `where { }`.

---

## 10. JOINs

Join with `join(table, type) { on { ... } }`. Use `JoinType.INNER` or `JoinType.LEFT`. Chain multiple `join` calls for more than two tables.

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

### Automatic column aliasing

`users` and `orders` both have an `id` and a `status`. To stop a raw cursor from silently overwriting one with the other, RoomQL aliases **colliding** columns as `table__column` (unique columns are left bare):

```sql
SELECT users.id AS users__id, name, age, users.status AS users__status,
       orders.id AS orders__id, userId, total, orders.status AS orders__status
FROM users INNER JOIN orders ON users.id = orders.userId
```

> Aliasing only happens when the primary table is a generated `*Table` (`from(UserEntityTable)`). With the raw-string overload `from("users")` there is no column metadata, so RoomQL emits `SELECT *` and does **not** alias.

### Mapping JOIN results

You provide your own result class. Map colliding columns with `@ColumnInfo(name = "table__column")`; unique columns map by their bare name:

```kotlin
data class UserOrder(
    @ColumnInfo(name = "name")       val userName: String,   // unique → bare
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

## 11. Suspend and Flow

The DSL is identical; only your DAO method's return type changes.

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

The `Flow` re-emits on every change to the tables listed in `@RawQuery(observedEntities = [...])`. Forget to list a table and the flow won't react to it.

---

## 12. The `.toQuery()` bridge

`query { }` returns a `RoomQlQuery` (pure JVM). Room's `@RawQuery` wants a `SupportSQLiteQuery`. `.toQuery()` (from `com.roomql.android`) bridges them:

```kotlin
import com.roomql.android.toQuery

val rql = query { from(UserEntityTable) }   // RoomQlQuery — inspect .sql / .args in tests
val support = rql.toQuery()                 // SupportSQLiteQuery — pass to the DAO
```

Keeping the two types separate is deliberate: it lets you unit-test the exact SQL and args (`rql.sql`, `rql.args`) on the JVM without a device, and only cross into Android at the DAO boundary.

---

## 13. Error handling

All validation is deferred to `build()` (which `query { }` calls for you) and throws `RoomQlException : RuntimeException`:

```kotlin
import com.roomql.runtime.RoomQlException

try {
    query { where { UserEntityTable.age gte 18 } }   // no from()!
} catch (e: RoomQlException) {
    // "from() must be called before build()"
}
```

Cases that throw:

| Mistake | Message |
|---|---|
| No `from()` | `from() must be called before build()` |
| `limit(0)` or negative | `limit() must be a positive integer, got 0` |
| `offset()` without `limit()` | `offset() requires limit() to be set` |
| `having { }` without `groupBy()` | `having() requires groupBy() to be set` |

Because these throw at build time (not while configuring), a half-built builder never blows up mid-DSL.

---

## 14. A complete repository

Putting it together — a repository that exposes list, suspend, reactive, and JOIN queries:

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

## 15. Testing your queries

Because `query { }` returns a plain `RoomQlQuery`, you can assert the generated SQL and args as pure JVM tests — no device, no Robolectric:

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
    assertContentEquals(arrayOf(18), q.args)
}
```

For full end-to-end coverage against a **real in-memory Room database** (JOIN results, `Flow` re-emission, null-skipping), see the runnable [`:sample`](../sample) module and run:

```
./gradlew :sample:testDebugUnitTest
```
