# RoomQL ksp-processor

The KSP processor behind **RoomQL**'s compile-time safety. It reads your Room `@Entity` classes and generates, in the same package, an `object <EntityName>Table` holding a typed `Column<T>` for every column — so a dynamic query built with the [`runtime`](../runtime) DSL references real Kotlin symbols instead of SQL strings.

```
io.github.kotplat.roomql:ksp-processor:2.0.0
```

```kotlin
plugins { id("com.google.devtools.ksp") }

dependencies { ksp("io.github.kotplat.roomql:ksp-processor:2.0.0") }
```

Given this entity:

```kotlin
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Int,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
```

the processor generates:

```kotlin
object UserEntityTable : EntityTable {
    override val tableName = "users"
    override val allColumnNames = listOf("id", "name", "created_at")
    val id: Column<Int> = Column("id", "users")
    val name: Column<String> = Column("name", "users")
    val createdAt: Column<Long> = Column("created_at", "users")   // @ColumnInfo name in SQL
}
```

`@Entity(tableName = ...)` and `@ColumnInfo(name = ...)` are respected, so the generated refs carry the real SQL names, and `@Ignore`d properties are skipped — they are not columns. Rename a property or a column and every query that used it stops compiling.

It also reads `@com.roomql.runtime.Projection`: annotate a result data class and it generates `<ClassName>Projection(...)` — one `Expression<T>` parameter per constructor property, aliased from `@ColumnInfo(name = ...)` — to spread into `select(...)` instead of hand-writing `alias` calls. See the [Usage Guide](../docs/USAGE.md#generating-the-projections-aliases-with-projection).

## Options

| KSP option | Default | Effect |
|---|---|---|
| `roomql.tableSuffix` | `Table` | Suffix appended to the entity class name to form the generated object name. `UserEntity` + `Table` = `UserEntityTable`. |

Set it in your module's `build.gradle.kts` if `Table` collides with a name you already use:

```kotlin
ksp {
    arg("roomql.tableSuffix", "Columns")   // generates UserEntityColumns instead
}
```

Requires **KSP `2.0.21-1.0.28`** (Kotlin 2.0.x); there is no KAPT variant.

**Full documentation: [the RoomQL README](../README.md) · [Usage Guide](../docs/USAGE.md)**

Licensed under the [Apache License, Version 2.0](../LICENSE).
