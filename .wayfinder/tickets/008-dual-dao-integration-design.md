# Dual DAO Integration Mode Design

`wayfinder:grilling` | status: closed | assignee: claude
parent: map.md
blocked-by: 004-dsl-grammar-prototype

## Question

The library must support two DAO integration modes. How exactly do they coexist, and what is the developer-facing API for each?

**Mode A — KSP-generated wiring** (zero boilerplate):
- Developer annotates a function or interface with something (`@DslQuery`?) and provides the DSL body
- KSP generates the Room `@Dao` implementation with `@RawQuery` wiring automatically
- What annotation triggers generation? What does the generated code look like?

**Mode B — Manual @RawQuery** (opt-in):
- Developer writes their own `@Dao` interface with `@RawQuery fun rawSearch(q: SupportSQLiteQuery): List<T>`
- Developer calls the DSL builder in their repository / data-source impl and passes the result to the DAO
- No KSP involvement — pure runtime

Resolve:

1. Can both modes coexist in the same project without conflict?
2. For Mode A: what is the exact annotation and generated output? Show the generated `@Dao` stub.
3. For Mode B: what utility does the library provide to make the manual path less painful? (A `roomQuery { }` top-level builder function? An extension on the DAO?)
4. How does the developer switch from Mode B to Mode A without breaking their existing DAO interface?
