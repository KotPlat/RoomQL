# KSP Code Generation Design

`wayfinder:grilling` | status: closed | assignee: claude
parent: map.md
blocked-by: 002-ksp-entity-scanning-research, 004-dsl-grammar-prototype

## Question

What code does the KSP processor generate for each `@Entity` class, and what code does it generate for Mode A DAO wiring?

Resolve:

1. **Column ref generation** — given `@Entity data class UserEntity(@ColumnInfo(name="user_age") val age: Int)`, what does the generated output look like?
   ```kotlin
   // Option A — companion object on the entity
   // Generated: UserEntity.age (Column<Int>)
   
   // Option B — separate generated file
   // Generated: UserEntityColumns.age (Column<Int>)
   ```
   Which option avoids conflicts with Room's own generated code?

2. **Column type**: What is the `Column<T>` type? A data class holding the SQL column name and Kotlin type token? What operators does it expose?

3. **Mode A DAO wiring** — for `@DslQuery fun findActiveAdults(...): List<User>`, what is the exact generated `@Dao` abstract class / implementation stub that Room's own KSP then picks up?

4. **Code gen tool**: Use `kotlinpoet` or `kotlinpoet-ksp`? Or raw string generation?

5. **Incremental vs. aggregating**: Can column-ref generation be incremental (per-file), or does it need aggregating mode (e.g. to detect cross-entity JOIN compatibility)?

6. **Namespace / package**: Where does KSP place the generated files — same package as the entity, or a dedicated `generated.roomquery` sub-package?
