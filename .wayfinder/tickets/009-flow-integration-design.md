# Flow + @RawQuery Integration Design

`wayfinder:grilling` | status: closed | assignee: claude
parent: map.md
blocked-by: 001-room-rawquery-research, 004-dsl-grammar-prototype

## Question

Room's reactive `@RawQuery` requires `observedEntities` — a list of entity classes whose table changes trigger re-emission. How does room-query-beauty handle this so that Flow queries "just work"?

Resolve:

1. **observedEntities inference**: Can the KSP processor infer `observedEntities` from the `from()` and `join()` clauses at compile time? Or must the developer declare them manually?

2. **API shape**: What does a Flow-returning DSL query look like?
   ```kotlin
   // Option A — explicit observedEntities
   val flow: Flow<List<User>> = dao.observeQuery(
     entities = [UserEntity::class],
     query = query { from(UserEntity) where { age gt 18 } }
   )
   
   // Option B — inferred by KSP, transparent to developer
   @DslQuery
   fun observeActiveAdults(age: Int): Flow<List<User>> = query {
     from(UserEntity) where { UserEntity.age gt age }
   }
   ```

3. **Suspend vs. Flow**: Should `query { }` return a one-shot `suspend` result or always a `Flow`? Or should there be two builder entry points (`query { }` for suspend, `observeQuery { }` for Flow)?

4. **Room's invalidation tracker**: Does using `@RawQuery` with Flow guarantee the same invalidation behaviour as `@Query` returning Flow? Surface any known limitations from the research ticket.
