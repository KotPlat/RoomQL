# RoomQL runtime

The **RoomQL** query DSL — a type-safe Kotlin builder for dynamic SQLite queries, with **no Android dependency**. This is the module that defines `query { }`, `Column<T>`, and the null-skipping condition operators.

```
io.github.kotplat.roomql:runtime:2.0.0
```

Pure JVM, so you can build a query and assert on the generated SQL in an ordinary unit test — no emulator, no Robolectric:

```kotlin
import com.roomql.runtime.SortDirection
import com.roomql.runtime.query

val q = query {
    from(UserEntityTable)
    where { UserEntityTable.age gteIfNotNull minAge }   // dropped when minAge is null
    orderBy(UserEntityTable.age, SortDirection.DESC)
}

assertEquals("SELECT * FROM users WHERE age >= ? ORDER BY age DESC", q.sql)
assertEquals(listOf(18), q.args)
```

`query { }` returns a `RoomQlQuery` — a `sql: String` plus a positional `args: List<Any?>`. To run it through Room's `@RawQuery`, add [`runtime-android`](../runtime-android) for the `.toQuery()` bridge, and [`ksp-processor`](../ksp-processor) to generate the `*Table` column references.

**Full documentation: [the RoomQL README](../README.md) · [Usage Guide](../docs/USAGE.md)**

Licensed under the [Apache License, Version 2.0](../LICENSE).
