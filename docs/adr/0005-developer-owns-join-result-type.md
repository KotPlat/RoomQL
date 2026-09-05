# JOIN result types are developer-supplied; no KSP-generated merge type

When a consumer writes a JOIN query, they supply their own result data class. RoomQl does not generate a merged entity type from the joined `@Entity` classes.

We chose this because generating a merge type requires KSP to reason about which fields the consumer actually wants from each joined table, which fields to rename for disambiguation, and how to map Room `@ColumnInfo` names to the merged class — a problem domain with many edge cases and no single right answer. Developer-supplied result classes also integrate naturally with Room's existing `@Embedded` and `@Relation` mapping, are already the pattern consumers know from manual `@RawQuery` use, and keep the generated code surface small.

## Considered Options

- **Developer-supplied result class (chosen)** — consumer defines the shape; KSP does not generate one.
- **KSP-generated merge type** — KSP combines fields from all joined entities into a generated class. Rejected: high complexity, many field-naming edge cases, and the generated type is rarely what the consumer actually needs.

## Consequences

When Column Aliasing renames columns to prevent collision (e.g. `user__id`, `order__id`), the developer's result data class field names must match those aliases. This is documented in the library README and surfaces early as a Room cursor-mapping error rather than a silent data bug.
