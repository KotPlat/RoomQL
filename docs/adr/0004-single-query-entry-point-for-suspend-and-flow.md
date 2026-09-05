# Single `query { }` entry point serves both suspend and Flow return types

The same `query { }` builder is used whether the consumer wants a one-shot suspend result or a reactive `Flow`. The return type declared on the `@QueryFunction`-annotated function (or on the manual `@RawQuery` method in Mode B) determines which Room execution path is used — not a different builder function.

We chose this because the query *shape* (which tables, which conditions, which sort) is independent of *how the result is consumed*. Separate entry points (`query { }` vs `observeQuery { }`) would require duplicating every query definition whenever consumption mode changes, and would introduce a naming asymmetry with no structural benefit.

## Considered Options

- **Single entry point (chosen)** — return type drives behaviour; one place to write the query.
- **Two entry points** — `query { }` for one-shot, `observeQuery { }` for reactive. Rejected: forces duplication and leaks consumption mode into the query definition.

## Consequences

In Mode A, KSP must inspect the return type of the `@QueryFunction` function to decide whether to generate a suspend call or a `Flow`-returning `@RawQuery` call. In Mode B, the developer is responsible for matching their `@RawQuery` declaration's return type to the Room path they intend.
