# RoomQL runtime-android

The Android bridge for **RoomQL**. One extension function: it adapts a `RoomQlQuery` — the output of the [`runtime`](../runtime) DSL — into the `SupportSQLiteQuery` that Room's `@RawQuery` methods accept.

```
io.github.kotplat.roomql:runtime-android:2.0.0
```

```kotlin
import com.roomql.android.toQuery

val q = query {
    from(UserEntityTable)
    where { UserEntityTable.status eqIfNotNull status }   // dropped when status is null
}
userDao.search(q.toQuery())   // @RawQuery fun search(q: SupportSQLiteQuery): List<UserEntity>
```

This module exists so the DSL itself can stay a plain-JVM library, unit-testable without an emulator. It depends on `androidx.sqlite` and requires **minSdk 21**; it is useless on its own, so add it alongside `runtime` and `ksp-processor` (both under `io.github.kotplat.roomql`).

**Full documentation: [the RoomQL README](../README.md) · [Usage Guide](../docs/USAGE.md)**

Licensed under the [Apache License, Version 2.0](../LICENSE).
