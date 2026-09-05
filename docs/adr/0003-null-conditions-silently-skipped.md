# Null-valued conditions are silently dropped at build time

When a consumer passes a null value to a Column operator (e.g. `UserEntity.age gt null`), the DSL produces a `Condition.Empty` that is filtered out when `build()` serializes the SQL. No exception is thrown; the condition simply does not appear in the WHERE clause.

We chose this because the dominant use case for a dynamic query DSL is optional filters: the caller has a nullable variable and only wants the condition when a value is present. The alternative — requiring non-null values and forcing the caller to write `if (age != null) { where { UserEntity.age gt age } }` — is exactly the boilerplate the library exists to eliminate.

## Considered Options

- **Silent skip (chosen)** — null means "no filter"; the builder absorbs it.
- **Throw at build time** — null is always a caller error; forces the caller to be explicit.
- **Require non-null at the type level** — operators only accept `T`, not `T?`; callers must guard before calling. Rejected: pushes the if-check back to the call site, defeating the purpose.

## Consequences

A consumer who accidentally passes null when they intend a filter will get a query that matches all rows — a silent widening of results rather than an error. This is a deliberate ergonomics trade-off. The library documents this contract prominently in the README.
