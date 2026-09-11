<p align="center">
  <img src="docs/banner.svg" alt="RoomQL — type-safe room query dsl" width="860"/>
</p>

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

### Option A — version catalog (`libs.versions.toml`)

In `gradle/libs.versions.toml`:

```toml
[versions]
roomql = "0.1.0"

[libraries]
roomql-annotations   = { module = "com.github.ahmednobii.RoomQL:roomql-annotations",   version.ref = "roomql" }
roomql-runtime       = { module = "com.github.ahmednobii.RoomQL:roomql-runtime",        version.ref = "roomql" }
roomql-ksp-processor = { module = "com.github.ahmednobii.RoomQL:roomql-ksp-processor",  version.ref = "roomql" }
```

In your module's `build.gradle.kts`:

```kotlin
implementation(libs.roomql.annotations)
implementation(libs.roomql.runtime)
ksp(libs.roomql.ksp.processor)
```

### Option B — string notation

```kotlin
implementation("com.github.ahmednobii.RoomQL:roomql-annotations:<version>")
implementation("com.github.ahmednobii.RoomQL:roomql-runtime:<version>")
ksp("com.github.ahmednobii.RoomQL:roomql-ksp-processor:<version>")
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
