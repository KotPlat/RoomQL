# JOIN Syntax Design

`wayfinder:grilling` | status: closed | assignee: claude
parent: map.md
blocked-by: 004-dsl-grammar-prototype

## Question

How are JOIN queries expressed in the DSL, and what is the return type when two or more tables are joined?

Resolve:

1. **Syntax**: What does an inner join and a left join look like in the builder?
   ```kotlin
   // Option A — method on the builder
   query<OrderWithUser> {
     from(OrderEntity)
     join(UserEntity, on = { OrderEntity.userId eq UserEntity.id })
   }
   
   // Option B — infix
   query<OrderWithUser> {
     from(OrderEntity) innerJoin UserEntity on { OrderEntity.userId eq UserEntity.id }
   }
   ```

2. **Return type**: Room requires the return type of a `@RawQuery` JOIN to be a POJO annotated with `@DatabaseView` or a manually mapped data class. How does the library handle this — does it require the developer to define the result type themselves, or does KSP generate it?

3. **Column ambiguity**: When two tables share a column name (e.g. both have `id`), how does the DSL qualify it? (`OrderEntity.id` vs `UserEntity.id`?)

4. **Chain limit**: Does the DSL support more than two-table joins in v1, or is that fog?

Pick the option for each and explain the trade-off.
