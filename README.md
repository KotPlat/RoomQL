# RoomQL

A type-safe Kotlin DSL for building dynamic Room queries at runtime — no raw strings, no reflection, no `@Query` boilerplate.

## Installation

Add the JitPack repository to your project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
    }
}
```

Then add the three dependencies in your module's `build.gradle.kts`:

```kotlin
implementation("com.github.ahmednobii.room-query-beauty:roomql-annotations:<version>")
implementation("com.github.ahmednobii.room-query-beauty:roomql-runtime:<version>")
ksp("com.github.ahmednobii.room-query-beauty:roomql-ksp-processor:<version>")
```

Replace `<version>` with the latest tag (e.g. `v0.1.0`).

## Usage

Annotate your Room entities as usual. The KSP processor generates a `*Columns` object for each:

```kotlin
@Entity(tableName = "users")
data class UserEntity(
    val id: Long,
    val name: String,
    val age: Int,
)
// → UserEntityColumns.id, UserEntityColumns.name, UserEntityColumns.age
```

Build queries with the DSL:

```kotlin
val query = query {
    from("users")
    where {
        UserEntityColumns.age gt 18
        UserEntityColumns.name like "A%"
    }
    orderBy(UserEntityColumns.name, SortDirection.ASC)
    limit(20)
}

@Dao
interface UserDao {
    @RawQuery
    fun getUsers(query: SupportSQLiteQuery): List<UserEntity>
}

// call site
dao.getUsers(query)
```

## License

MIT
