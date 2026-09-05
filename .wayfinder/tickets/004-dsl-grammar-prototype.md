# DSL Grammar Design — Prototype

`wayfinder:prototype` | status: closed | assignee: claude
parent: map.md
blocked-by: 001-room-rawquery-research, 003-prior-art-survey
blocks: 007-join-syntax-design, 008-dual-dao-integration-design, 009-flow-integration-design, 010-error-model-design

## Question

What is the exact Kotlin DSL grammar for room-query-beauty? Build a rough but concrete prototype — enough to react to — covering:

1. **Entry point**: `query { }` lambda receiver — what type is the receiver? (`QueryBuilder<T>`?)
2. **FROM clause**: How does `from(UserEntity)` work? Does it take a `KClass`, a generated token, or a companion object? What does the KSP-generated column ref look like (`UserEntity.age` or `UserEntity.Columns.age`)?
3. **WHERE clause**: How are conditions composed? Operator overloading (`infix fun Column.eq(value: T): Condition`)? How does `and`/`or` nest?
4. **ORDER BY**: `orderBy(UserEntity.name, DESC)` — type of the column ref and direction enum?
5. **LIMIT / OFFSET**: Fluent suffix (`limit(10).offset(20)`) or top-level in builder?
6. **GROUP BY / HAVING**: Syntax for `groupBy(UserEntity.status) having { count() gt 5 }`?
7. **Nullability**: How are nullable filter values handled — does `null` mean "skip this condition" automatically?

Produce a short Kotlin code snippet (not a working implementation — just the surface API) showing a representative query covering WHERE + ORDER BY + LIMIT and another covering GROUP BY + HAVING. Then note any open sub-questions the prototype surfaces.

## Resolution

**Entry point:** `query { }` top-level function

**Column refs:** KSP generates extension properties on the entity companion — `UserEntity.age`, `UserEntity.status`

**WHERE combinator:** AND by default inside `where { }`; wrap in `or { }` for OR groups

**Null handling:** `null` values automatically skip their condition — no if-checks needed outside the DSL

**Operators (v1):**
- `eq` / `notEq`
- `gt` / `gte` / `lt` / `lte`
- `like` / `notLike`
- `isNull()` / `isNotNull()`
- `inList` / `notInList`
- `between`
- `contains` (sugar for `LIKE %value%`)

**ORDER BY:** `orderBy(column, ASC|DESC)` — chainable for multi-column sort

**LIMIT / OFFSET:** `limit(n)` and `offset(n)` as separate builder calls

**GROUP BY / HAVING:** `groupBy(column)` + `having { condition }` as sibling blocks

**Canonical query shape:**
```
query {
  from(UserEntity)
  where {
    UserEntity.age gt age          // null → skipped
    UserEntity.status eq status    // null → skipped
    or {
      UserEntity.role eq "admin"
      UserEntity.role eq "mod"
    }
  }
  orderBy(UserEntity.name, ASC)
  limit(20)
  offset(40)
}
```
