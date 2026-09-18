# Contributing to RoomQL

Thanks for considering a contribution. RoomQL is a small, deliberately narrow library — a type-safe
Kotlin DSL for dynamic Android Room queries — so the most useful contributions are usually bug
reports with a reproducing query, and focused pull requests.

## Before you open a pull request

Open an issue first for anything beyond a typo or an obvious bug fix. RoomQL's public API is
frozen by [Binary Compatibility Validator](https://github.com/Kotlin/binary-compatibility-validator),
and v1 has a deliberately small surface — a feature that widens it needs a discussion before it
needs code.

## Branches

| Branch | What it is |
|---|---|
| `master` | v1 — the three published artifacts. Target your PRs here. |
| `development` | v2 exploration, including the annotation-driven integration dropped from v1. |

## Requirements

- **JDK 17** (the build sets `jvmToolchain(17)`)
- Kotlin 2.0.x / KSP `2.0.21-1.0.28` — both pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml)
- No Android SDK needed for `:runtime` or `:ksp-processor`; `:runtime-android` and `:sample` need one.

## The build

```bash
./gradlew assemble                  # compile everything
./gradlew test                      # JVM unit tests (:runtime, :ksp-processor)
./gradlew :sample:testDebugUnitTest # end-to-end Robolectric tests against in-memory Room
./gradlew apiCheck                  # verify the public API surface has not drifted
```

CI runs `assemble`, `test`, and `apiCheck` on every push and pull request to `master`. All three
must pass.

### If you change the public API

`apiCheck` will fail until you regenerate the `.api` dumps:

```bash
./gradlew apiDump
```

Commit the updated `*/api/*.api` files with your change. Review that diff for an unintended entry
— that's usually a missing `internal` — but do not treat an *unchanged* diff as proof the change is
source-compatible. The `.api` format is a JVM signature dump: `T & Any` and `T?` erase to the same
`Ljava/lang/Object;`, so a change that only tightens or loosens nullability, or narrows a generic
bound, produces no diff here at all while still breaking every caller who passes the case you
removed. `apiCheck` passing is evidence of binary compatibility, not source compatibility — treat
any nullability or generic-bound change as a major-version change regardless of what the dump shows.

## Code conventions

- **`explicitApi()` is on** in all three published modules. Every public declaration needs an explicit
  visibility modifier and an explicit return type. Anything not meant for users is `internal`.
- Match the surrounding style. The DSL leans on infix extension functions scoped by `@RoomQlDsl`.
- **Every value-taking operator ships two forms.** A required form (`eq`, `gte`, …) takes `T & Any`
  (or `String`, `List<T & Any>` for the list operators) and will not compile against a nullable
  value. An optional form, suffixed `IfNotNull` (`IfNotEmpty` for the two list operators — an empty
  list has to skip too, not just a null one), takes the nullable type and skips the condition when
  the value is absent. Keep a new operator's naming and split consistent with the existing ones;
  see `ConditionScope.kt`'s class-level KDoc for the reasoning. `between` is the one exception with
  no optional form — see the comment above it before adding `betweenIfNotNull`.

## Tests

Every change needs a test.

- DSL changes: assert on the generated `sql` and `args` of a `RoomQlQuery` in `:runtime` — that is
  what the pure-JVM split is for, so no test there should need Android.
- KSP changes: `:ksp-processor` tests compile a source snippet and assert on the generated output.
- Anything touching the Room boundary: add a `:sample` test that runs the query against in-memory Room.

## Commit messages

One commit per logical change, subject line in this format:

```
[IssueNumber] short descriptive message
```

For example: `[34] restrict like/notLike/contains to String columns`.

## Documentation

A change users can observe needs its documentation updated in the same PR:

- [`README.md`](README.md) — the API reference table, operator table, or limitations list
- [`docs/USAGE.md`](docs/USAGE.md) — if the change adds or alters an example
- [`CHANGELOG.md`](CHANGELOG.md) — an entry under `## [Unreleased]`
- The module README ([`runtime`](runtime/README.md), [`runtime-android`](runtime-android/README.md),
  [`ksp-processor`](ksp-processor/README.md)) if the change is specific to one module

## Reporting bugs

Open a [GitHub issue](https://github.com/ahmednobii/RoomQL/issues) with the `query { }` block you
wrote, the SQL you expected, the SQL or exception you got, and your RoomQL, Room, Kotlin, and KSP
versions. A failing assertion on `RoomQlQuery.sql` is the ideal report — it needs no device.

**Security vulnerabilities do not go in public issues.** Follow [SECURITY.md](SECURITY.md) instead.

## License

By contributing, you agree that your contributions are licensed under the
[Apache License, Version 2.0](LICENSE).
