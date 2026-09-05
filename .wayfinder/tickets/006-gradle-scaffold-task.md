# Gradle Project Scaffold

`wayfinder:task` | status: open | assignee: —
parent: map.md
blocked-by: (unblocked — 005 closed)

## Question

Set up the Gradle multi-module project structure for room-query-beauty based on the module structure decision.

Work to do (AFK — agent drives this):

1. Initialise a Gradle Kotlin DSL project at the repo root with `settings.gradle.kts` declaring all modules
2. Create each module directory with a minimal `build.gradle.kts` — correct plugin declarations (KSP, Android library or pure Kotlin), inter-module dependencies, and Room dependency where needed
3. Add a `.gitignore`, `gradle/libs.versions.toml` version catalog with Room, KSP, and Kotlin versions pinned
4. Verify the project syncs cleanly (`./gradlew projects`)

Record what was created and the exact module paths so downstream tickets can reference them.
