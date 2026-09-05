# Error Model Design — KSP + Runtime

`wayfinder:grilling` | status: closed | assignee: claude
parent: map.md
blocked-by: 004-dsl-grammar-prototype, 005-module-structure-grilling

## Question

What do errors look like for library consumers — both at KSP compile time and at runtime?

Resolve:

1. **KSP compile-time errors** — what can KSP reliably catch?
   - Using a column from the wrong entity in a `where { }` block?
   - Calling `groupBy` without a matching `from`?
   - Missing `@RawQuery` return type annotation?
   
2. **KSP error message style**: Should they use `KSPLogger.error(message, symbol)` pointing at the call site? What message format? Example: `"room-query-beauty: Column 'UserEntity.age' used in WHERE but entity 'UserEntity' is not in the FROM clause."`

3. **Runtime exceptions** — what dynamic cases can't be caught at compile time?
   - Fully dynamic column refs built from user input strings?
   - `LIMIT` set to a negative value?
   - Empty `WHERE` clause when one is required?
   
4. **Exception type**: Custom `RoomQueryException` or reuse `IllegalArgumentException`/`IllegalStateException`?

5. **Fail-fast vs. deferred**: Should the query builder validate eagerly (throw when `.where { }` is called with bad input) or lazily (throw when `.build()` / `.execute()` is called)?

Pick a position on each and give the rationale.
