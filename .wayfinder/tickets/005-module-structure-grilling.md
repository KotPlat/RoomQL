# Gradle Module Structure

`wayfinder:grilling` | status: closed | assignee: claude
parent: map.md
blocks: 006-gradle-scaffold-task

## Question

What Gradle modules make up the room-query-beauty library, and what are the dependency edges between them?

Resolve:

1. **How many modules?** Candidates:
   - `:annotations` — lightweight, holds `@DslQuery` and other markers, zero dependencies
   - `:runtime` — the DSL builder classes, `QueryBuilder`, `Condition`, `SupportSQLiteQuery` wrappers; depends on Room runtime
   - `:ksp-processor` — the KSP processor that reads `@Entity` and generates `Columns` objects + optional `@RawQuery` wiring; depends on `:annotations` + KSP API
   - `:testing` — helpers for unit-testing DSL-built queries without a real database
   
2. **Does the consumer need to apply two plugins** (Room's KSP + this library's KSP), or can this library's processor piggyback on the same KSP run?

3. **What does the consumer's `build.gradle.kts` look like** — the minimal dependency block to wire up the library?

4. **Where does the `:core:query-dsl` layer-enforcement module live** in the multi-module app that consumes the library — is it the `:runtime` module renamed, or a separate wrapper?

Produce the definitive module graph: boxes + arrows, then the minimal `build.gradle.kts` snippet for a consumer app's `:data` module.

## Resolution

**3 modules:**

```
:annotations  ──►  :runtime  ──►  (consumer :data module)
     │
     └──────────►  :ksp-processor  ──►  (consumer ksp() dep)
```

- `:annotations` — zero external deps; holds `@DslQuery`, `Column<T>` marker type
- `:runtime` — depends on `:annotations` + Room runtime; holds `QueryBuilder`, `Condition`, DSL entry-point functions
- `:ksp-processor` — depends on `:annotations` + KSP API; reads `@Entity`, generates `Columns` objects and optional `@RawQuery` wiring

**KSP wiring:** Both RoomQl's processor and Room's own compiler run in the same KSP invocation — no separate Gradle plugin needed.

**Layer enforcement:** Gradle module graph is the wall. Only the consumer's `:data` modules declare `roomql-runtime` as a dependency; `:domain` and `:presentation` do not.

**Consumer `build.gradle.kts`:**
```kotlin
dependencies {
    implementation("io.github.ahmednobi:roomql-runtime:1.0.0")
    ksp("io.github.ahmednobi:roomql-ksp-processor:1.0.0")
    ksp("androidx.room:room-compiler:2.6.1") // existing Room dep
}
```

**`:testing` module:** deferred to v2 — out of scope for v1.
