# RoomQL Cheat Sheet

A task-oriented lookup for RoomQL's condition operators, aggregates, and projection functions — organized by intent, not alphabetically. For worked examples see the [Usage Guide](USAGE.md); for full signatures and generated SQL see the [API Reference](API.md).

Most of this maps 1:1 onto SQL you already know (`eq`→`=`, `gt`→`>`, `count`/`sum`/`avg`/`min`/`max`). The one RoomQL-specific convention is the `IfNotNull`/`IfNotEmpty` suffix — every table below puts the required and optional form of an operator side by side so that convention is obvious at a glance.

---

## Conditions (`where { }` / `having { }`)

| I want to... | Use | Generated SQL |
|---|---|---|
| Require a filter always | `eq`, `notEq`, `gt`, `gte`, `lt`, `lte` | `col = ?`, `col != ?`, `col > ?`, `col >= ?`, `col < ?`, `col <= ?` |
| Skip a filter when the value is `null` | `eqIfNotNull`, `notEqIfNotNull`, `gtIfNotNull`, `gteIfNotNull`, `ltIfNotNull`, `lteIfNotNull` | same, or dropped entirely |
| Match `NULL` itself | `isNull`, `isNotNull` | `col IS NULL`, `col IS NOT NULL` |
| Require a text match | `like`, `notLike`, `contains` | `col LIKE ?` (verbatim pattern), `col NOT LIKE ?`, `col LIKE ?` (auto-wrapped in `%`) |
| Skip a text match when the value is `null` | `likeIfNotNull`, `notLikeIfNotNull`, `containsIfNotNull` | same, or dropped entirely |
| Require set membership | `inList`, `notInList` | `col IN (?,?)` (empty list → matches **nothing**), `col NOT IN (?)` (empty list → matches **everything**) |
| Skip set membership when the list is `null` or empty | `inListIfNotEmpty`, `notInListIfNotEmpty` | same, or dropped entirely |
| Require a range | `between(lower, upper)` | `col BETWEEN ? AND ?` — both bounds required, no `IfNotNull` form |
| Allow either bound of a range to be absent | compose `gteIfNotNull` + `lteIfNotNull` | whichever bound is present, independently |
| Group alternatives with OR | `or { ... }` | `(cond OR cond)`, parenthesised and AND-combined with the rest |

**Rule of thumb:** the suffix tells you what happens when the value is absent. No suffix → the condition is always applied (and a nullable value won't even compile). `IfNotNull`/`IfNotEmpty` → the condition disappears from the SQL. Absent is never the same as SQL `NULL` — use `isNull`/`isNotNull` for that.

`where { }` accepts `Column<T>` only. `having { }` accepts any `Expression<T>` — columns and aggregates alike — because SQL allows aggregates in `HAVING` but not in `WHERE`.

---

## Sorting, paging, grouping

| I want to... | Use |
|---|---|
| Sort by one or more columns | `orderBy(expr, SortDirection.ASC / .DESC)` — call repeatedly for a multi-column sort |
| Sort by an aggregate | `orderBy(count(...), SortDirection.DESC)` — `orderBy` takes any `Expression<T>` |
| Page results | `limit(n)` (must be positive), `offset(n)` (requires `limit`) |
| Group rows | `groupBy(column)` — call repeatedly for a multi-column `GROUP BY` |
| Filter a group | `having { ... }` — requires `groupBy(...)` to be set |

---

## Aggregating a group

| I want to... | Use | Notes |
|---|---|---|
| Count matching rows | `count(column)` | `NULL` values in `column` aren't counted; under a `LEFT JOIN`, an unmatched row counts as 0 |
| Count all rows, including `NULL`s | `countAll()` | Under a `LEFT JOIN`, an unmatched row counts as 1 — the difference from `count(column)` |
| Sum / average a numeric column | `sum(column)`, `avg(column)` | Constrained to numeric column types; `avg` always returns `Double?` |
| Min / max of any column | `min(column)`, `max(column)` | Works on any orderable type, not just numeric |

All six return a nullable `Expression<T>` — SQL returns `NULL` for an empty group.

---

## Projecting specific columns (`select(...)`)

| I want to... | Use |
|---|---|
| Return whole rows (the default) | Leave `select(...)` out entirely — `JOIN`-collision columns are aliased automatically |
| Return a single scalar value | `select(countAll())`, `select(avg(col))`, `select(min(col))`, ... — any single unaliased expression works, `count`/`countAll` included; Room binds it straight to `Int`/`Long`/`Double`, no name needed |
| Return several named columns/aggregates | `select(col alias "name", count(other) alias "other_count", ...)` |
| Generate those aliases from a result class instead of hand-writing them | Annotate the class `@Projection` — KSP generates `<ClassName>Projection(...)`, spread it into `select(*...)` |

`select(...)` switches off automatic JOIN-collision aliasing — its argument list becomes the complete, explicit set of returned columns. `build()` rejects a grouped projection with a bare column missing from `GROUP BY`, and an ungrouped projection mixing an aggregate with a bare column — both would otherwise let SQLite pick an arbitrary row's value silently.

---

## Joining tables

| I want to... | Use |
|---|---|
| Join two tables | `join(OtherTable, JoinType.INNER) { on { ColumnA eq ColumnB } }` |
| Keep unmatched left-side rows | `JoinType.LEFT` instead of `.INNER` |
| Join more than two tables | Chain additional `join(...)` calls |

Joining requires `from(EntityTable)` (not the raw-string `from(String)` overload) so RoomQL has the column metadata to detect and alias name collisions as `table__column`.

---

## Errors `build()` throws

| Mistake | `RoomQlException` message |
|---|---|
| No `from()` | `from() must be called before build()` |
| `limit(0)` or negative | `limit() must be a positive integer, got 0` |
| `offset()` without `limit()` | `offset() requires limit() to be set` |
| `having { }` without `groupBy()` | `having() requires groupBy() to be set` |
| `join()` after `from(String)` | `join() requires from(EntityTable) so columns can be aliased; from(String) has no column metadata` |
| `select(...)` grouped bare column missing from `groupBy()` | `select { } column(s) not in groupBy(): <names>` |
| `select(...)` mixing an aggregate with a bare column, ungrouped | `select { } cannot mix an aggregate with a bare column unless groupBy() is set` |

Full detail on every entry above, including exact signatures and more generated-SQL examples, is in the [API Reference](API.md) and [Usage Guide](USAGE.md).
