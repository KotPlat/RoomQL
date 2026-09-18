# RoomQL demo app

A Compose product-catalogue app that makes RoomQL's argument tappable: the flagship screen implements the same search **four different ways** and lets you switch between them at runtime while showing the SQL each one produces.

This is a **standalone Gradle build**. It consumes RoomQL as published artifacts — the same three coordinates the [main README](../README.md#installation) documents — rather than as project dependencies, so building it also verifies that the documented install path actually works.

## Running it

```bash
./gradlew -p demo assembleDebug        # from the repository root
./gradlew -p demo testDebugUnitTest
```

Until RoomQL 2.0.0 is tagged and published on JitPack, build the library locally first — this is exactly what `jitpack.yml` runs when building a tag:

```bash
VERSION=2.0.0 ./gradlew publishToMavenLocal
./gradlew -p demo assembleDebug
```

`demo/settings.gradle.kts` lists `mavenLocal()` ahead of JitPack, so a locally published build wins when present.

## Opening it in Android Studio

Open the **`demo/` directory itself** as the project (File -> Open -> select `demo/`), not the
repository root. The demo is intentionally absent from the root `settings.gradle.kts`, so a root
project window has no module owning `demo/src/**` and reds every import in it while `./gradlew -p
demo build` still succeeds. Publish the library first (`VERSION=2.0.0 ./gradlew publishToMavenLocal`
from the root), then sync — otherwise `mavenLocal()` has nothing for the IDE to resolve.

To work on the library and the demo in one window instead, add the demo as a second linked Gradle
project from the Gradle tool window's **+** button. Avoid wiring it in with `includeBuild`: that
substitutes the `io.github.kotplat.roomql:*` coordinates for project dependencies, which is
exactly the install path this build exists to verify.

## The screens

| Screen | What it shows |
|---|---|
| **Multi-filter search** | Four optional filters, four implementations, one result set. The exhibit. |
| **Faceted catalogue** | Multi-select chips driving `IN (...)`, joined to brands — including RoomQL's collision aliasing. |
| **Sortable, paginated** | Sort column and direction chosen at runtime, with `LIMIT`/`OFFSET` paging. |
| **Search as you type** | Debounced `contains` over a `Flow`, and the `observedEntities` trap. |

## The four implementations

The flagship screen's strategy selector switches between these at runtime. All four return identical rows — [`FlagshipSearchTest`](src/test/kotlin/com/roomql/demo/FlagshipSearchTest.kt) asserts exactly that across all 16 filter combinations, because a subtly wrong baseline would make the whole comparison dishonest.

| Strategy | Approach | What it costs |
|---|---|---|
| **RoomQL** | One `query { }` block | No compile-time verification of the whole SQL |
| **IS NULL OR** | One `@Query` with `(:x IS NULL OR col = :x)` per filter | SQL never shrinks; barely readable; cannot sort dynamically |
| **Overloaded DAO** | One `@Query` per filter combination — [16 of them](src/main/kotlin/com/roomql/demo/data/OverloadedProductDao.kt) | Doubles with every new filter |
| **String concat** | Hand-built `SimpleSQLiteQuery` | Unchecked column names, manual argument ordering |

The 16-method DAO is deliberately written out in full. The tedium is the argument.

## Notes

- The catalogue is ~500 deterministically generated rows, seeded on first launch, so runs and tests are reproducible.
- `minSdk 21`, `targetSdk 36`. Robolectric runs the tests pinned to SDK 34, the newest it supports.
- Looking for the **minimal** RoomQL example instead? See [`MinimalExample.kt`](../sample/src/main/kotlin/com/roomql/sample/MinimalExample.kt) — one annotated file with an entity, a `@RawQuery` DAO, a database, and a single query. [`:sample`](../sample) covers the full stack in Robolectric tests.
