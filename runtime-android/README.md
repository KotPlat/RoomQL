# roomql-runtime-android

The Android bridge for **RoomQL**. One extension function: it adapts a `RoomQlQuery` — the output of the [`roomql-runtime`](../runtime) DSL — into the `SupportSQLiteQuery` that Room's `@RawQuery` methods accept.

```
io.github.kotplat.roomql:runtime-android:1.0.0
```

```kotlin
import com.roomql.android.toQuery

val q = query {
    from(UserEntityTable)
    where { UserEntityTable.status eq status }   // dropped when status is null
}
userDao.search(q.toQuery())   // @RawQuery fun search(q: SupportSQLiteQuery): List<UserEntity>
```

This module exists so the DSL itself can stay a plain-JVM library, unit-testable without an emulator. It depends on `androidx.sqlite` and requires **minSdk 21**; it is useless on its own, so add it alongside `roomql-runtime` and `roomql-ksp-processor`.

**Full documentation: [the RoomQL README](../README.md) · [Usage Guide](../docs/USAGE.md)**

Licensed under the [Apache License, Version 2.0](../LICENSE).
