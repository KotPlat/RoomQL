# KSP Entity Scanning API Research

`wayfinder:research` | status: closed | assignee: claude
parent: map.md | blocks: 005-ksp-codegen-design

## Question

How does KSP read Room's `@Entity`, `@ColumnInfo`, `@Embedded`, and `@Relation` annotations to extract column metadata — and what API does a custom KSP processor use to generate Kotlin source from it?

Specifically:

1. What KSP APIs (`KSClassDeclaration`, `KSAnnotation`, etc.) are needed to find all `@Entity`-annotated classes in a compilation unit?
2. How does Room itself handle `@ColumnInfo(name = "...")` overrides vs. the property name? How should this library replicate that mapping?
3. How are `@Embedded` fields handled — do they contribute nested columns to the parent table's column set?
4. What does a minimal KSP processor look like that generates a `Columns` companion object for each `@Entity`? (Reference Room's own KSP processor source if available.)
5. What are the KSP incremental processing constraints — does entity scanning need to be non-incremental (aggregating mode)?
6. What is the minimum KSP version compatible with Room 2.6 / KSP 1.x?

Consult the KSP documentation, Room KSP source (`androidx.room:room-compiler-processing`), and any community KSP tutorials.

## Resolution

Sources consulted: KSP Quickstart (kotlinlang.org/docs/ksp-quickstart.html), KSP Incremental (kotlinlang.org/docs/ksp-incremental.html), KSP `Resolver.kt` and `KSClassDeclaration.kt` source on github.com/google/ksp, Room3 compiler source fragments via github.com/androidx/androidx search (room3/room3-compiler), KSP gradle integration tests (google/ksp GradleCompilationTest.kt), Room release notes (developer.android.com/jetpack/androidx/releases/room).

---

### Q1 — KSP APIs for finding all @Entity-annotated classes

The single entry point is `Resolver.getSymbolsWithAnnotation`:

```kotlin
// Source: google/ksp – api/src/main/kotlin/com/google/devtools/ksp/processing/Resolver.kt
fun getSymbolsWithAnnotation(
    annotationName: String,
    inDepth: Boolean = false,   // true = also checks local/nested declarations (expensive)
): Sequence<KSAnnotated>
```

Call it with the fully-qualified annotation name, cast results to `KSClassDeclaration`, and validate them before processing:

```kotlin
override fun process(resolver: Resolver): List<KSAnnotated> {
    val (valid, deferred) = resolver
        .getSymbolsWithAnnotation("androidx.room.Entity")
        .filterIsInstance<KSClassDeclaration>()
        .partition { it.validate() }

    valid.forEach { processEntity(it) }
    return deferred   // re-queued for the next round
}
```

Key types in play (all from `com.google.devtools.ksp.*`):

| Type | Role |
|---|---|
| `Resolver` | Injected into `SymbolProcessor.process()`; gateway to symbol lookup |
| `KSClassDeclaration` | Represents the annotated class; exposes `simpleName`, `qualifiedName`, `getAllProperties()`, `annotations` |
| `KSAnnotation` | One annotation instance; has `shortName`, `arguments: List<KSValueArgument>` |
| `KSValueArgument` | One key/value pair inside an annotation; `name: KSName`, `value: Any?` |
| `KSPropertyDeclaration` | A class property; exposes its own `annotations` sequence |

Room's own processing layer (`room3-compiler-processing`, `KspRoundEnv.kt`) wraps this call identically:

```kotlin
// androidx/androidx – room3/room3-compiler-processing/.../ksp/KspRoundEnv.kt
env.resolver.getSymbolsWithAnnotation(annotationQualifiedName).forEach { symbol ->
    when (symbol) {
        is KSPropertyDeclaration -> env.wrapPropertyDeclaration(symbol)…
        …
    }
}
```

---

### Q2 — @ColumnInfo(name="…") overrides vs property names

Room's `PropertyProcessor.kt` (room3/room3-compiler) applies this logic verbatim:

```kotlin
// Simplified from androidx/androidx – room3/room3-compiler/.../PropertyProcessor.kt
val annotationColumnName: String? =
    element.annotations
        .firstOrNull { it.shortName.asString() == "ColumnInfo" }
        ?.arguments
        ?.firstOrNull { it.name?.asString() == "name" }
        ?.value as? String

val rawCName: String =
    if (annotationColumnName != null && annotationColumnName != ColumnInfo.INHERIT_FIELD_NAME) {
        annotationColumnName          // explicit override wins
    } else {
        element.simpleName.asString() // fall back to the Kotlin property name
    }

val columnName = (propertyParent?.prefix ?: "") + rawCName   // prefix injected by @Embedded parent
```

`ColumnInfo.INHERIT_FIELD_NAME` is the sentinel value `""` (empty string), which is the default for `@ColumnInfo(name = ColumnInfo.INHERIT_FIELD_NAME)` — meaning "do not override, use the property name".

A custom processor should replicate this with pure KSP APIs:

```kotlin
fun KSPropertyDeclaration.columnName(embeddedPrefix: String = ""): String {
    val columnInfoAnnotation = annotations
        .firstOrNull { it.shortName.asString() == "ColumnInfo" }
    val explicitName = columnInfoAnnotation
        ?.arguments
        ?.firstOrNull { it.name?.asString() == "name" }
        ?.value as? String
    val rawName = if (!explicitName.isNullOrEmpty()) explicitName
                  else simpleName.asString()
    return embeddedPrefix + rawName
}
```

Source: `room3/room3-compiler/src/main/kotlin/androidx/room3/processor/PropertyProcessor.kt` (androidx/androidx), confirmed by `DataClassProcessorTest.kt` assertion:
`assertThat(dataClass.properties[2].columnName, is("fooy2"))` where `prefix="foo"` and `@ColumnInfo(name="y2")`.

---

### Q3 — @Embedded fields and nested columns

Yes. `@Embedded` fields contribute their own properties (flattened, with an optional prefix) directly to the parent table's column set — there is no nesting in the SQLite schema.

Room's `DataClassProcessor.kt` handles this recursively. For each property annotated with `@Embedded`:

1. Read the `prefix` argument from the `@Embedded` annotation (default `""`).
2. Recursively call the data-class processor on the embedded type, passing `inheritedPrefix + propertyPrefix` down the call stack.
3. Each leaf property's `columnName` is computed as `(accumulated prefix) + rawCName`.

The test confirms:
```
// DataClassProcessorTest.kt (room3-compiler)
// @Embedded(prefix = "foo") Coords coords  where Coords has: val x: Int, @ColumnInfo(name="y2") val y: Int
assertThat(dataClass.properties[1].columnName, is("foox"))
assertThat(dataClass.properties[2].columnName, is("fooy2"))
assertThat(parent.prefix, is("foo"))
```

A custom processor replicates this with a recursive walk:

```kotlin
fun collectColumns(
    classDecl: KSClassDeclaration,
    prefix: String = "",
): List<String> = buildList {
    for (prop in classDecl.getAllProperties()) {
        val embedded = prop.annotations
            .firstOrNull { it.shortName.asString() == "Embedded" }
        if (embedded != null) {
            val childPrefix = prefix +
                (embedded.arguments.firstOrNull { it.name?.asString() == "prefix" }
                    ?.value as? String ?: "")
            val embeddedType = prop.type.resolve().declaration as? KSClassDeclaration
            if (embeddedType != null) addAll(collectColumns(embeddedType, childPrefix))
        } else {
            add(prop.columnName(prefix))   // uses the helper from Q2
        }
    }
}
```

`@Relation` fields are NOT columns — they are omitted from column collection entirely (Room skips them in `DataClassProcessor`).

Source: `room3/room3-compiler/src/main/kotlin/androidx/room3/processor/DataClassProcessor.kt` and `PropertyProcessor.kt` (androidx/androidx).

---

### Q4 — Minimal KSP processor generating a `Columns` companion object per @Entity

Based on the KSP quickstart example and the patterns established above, a complete minimal skeleton:

**`EntityColumnsProcessor.kt`**

```kotlin
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate

class EntityColumnsProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val (valid, deferred) = resolver
            .getSymbolsWithAnnotation("androidx.room.Entity")
            .filterIsInstance<KSClassDeclaration>()
            .partition { it.validate() }

        valid.forEach { generate(it) }
        return deferred
    }

    private fun generate(entity: KSClassDeclaration) {
        val pkg = entity.packageName.asString()
        val name = entity.simpleName.asString()
        val columns = collectColumns(entity)

        val file = codeGenerator.createNewFile(
            // aggregating = true because output depends on ALL @Entity classes in the compilation
            dependencies = Dependencies(aggregating = true, *entity.containingFile?.let { arrayOf(it) } ?: emptyArray()),
            packageName = pkg,
            fileName = "${name}Columns",
        )
        file.bufferedWriter().use { w ->
            w.write("package $pkg\n\n")
            w.write("object ${name}Columns {\n")
            for (col in columns) {
                val constName = col.uppercase().replace(Regex("[^A-Z0-9]"), "_")
                w.write("    const val $constName = \"$col\"\n")
            }
            w.write("}\n")
        }
    }

    private fun collectColumns(
        classDecl: KSClassDeclaration,
        prefix: String = "",
    ): List<String> = buildList {
        for (prop in classDecl.getAllProperties()) {
            // Skip @Relation – not a column
            if (prop.annotations.any { it.shortName.asString() == "Relation" }) continue

            val embedded = prop.annotations
                .firstOrNull { it.shortName.asString() == "Embedded" }
            if (embedded != null) {
                val childPrefix = prefix +
                    (embedded.arguments.firstOrNull { it.name?.asString() == "prefix" }
                        ?.value as? String ?: "")
                val embeddedType =
                    prop.type.resolve().declaration as? KSClassDeclaration
                if (embeddedType != null) addAll(collectColumns(embeddedType, childPrefix))
            } else {
                add(prop.columnName(prefix))
            }
        }
    }

    private fun KSPropertyDeclaration.columnName(embeddedPrefix: String): String {
        val explicitName = annotations
            .firstOrNull { it.shortName.asString() == "ColumnInfo" }
            ?.arguments
            ?.firstOrNull { it.name?.asString() == "name" }
            ?.value as? String
        val raw = if (!explicitName.isNullOrEmpty()) explicitName else simpleName.asString()
        return embeddedPrefix + raw
    }
}
```

**`EntityColumnsProcessorProvider.kt`**

```kotlin
import com.google.devtools.ksp.processing.*

class EntityColumnsProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        EntityColumnsProcessor(environment.codeGenerator, environment.logger)
}
```

**`META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider`**

```
com.example.EntityColumnsProcessorProvider
```

Sources: KSP quickstart (kotlinlang.org/docs/ksp-quickstart.html), KSP `Resolver.kt` / `KSClassDeclaration.kt` source (google/ksp), Room `PropertyProcessor.kt` / `DataClassProcessor.kt` patterns (androidx/androidx).

---

### Q5 — Aggregating vs isolating mode for entity scanning

**Entity scanning must use aggregating mode** (`Dependencies(aggregating = true, …)`).

From the KSP incremental processing doc (kotlinlang.org/docs/ksp-incremental.html):

> Aggregating outputs can be affected by changes in any source file. Any input change triggers a rebuild of all aggregating outputs.

`getSymbolsWithAnnotation("androidx.room.Entity")` queries across the entire compilation unit — any source file could add, remove, or rename an entity. Because the set of entities drives what files are generated, the processor cannot know which input files are "irrelevant"; adding a new `@Entity` class in a previously-unrelated file changes the output. That makes every generated `Columns` object an aggregating output.

If the processor instead generated one file per entity and only ever read properties from that entity's own source file (isolating mode), incremental builds would be faster but only safe if the processor guarantees a strictly 1:1 source→output relationship with no cross-file coordination.

Room's own KSP filer (`KspFiler.kt`, room3-compiler-processing) chooses `Dependencies.ALL_FILES` (the most conservative aggregating variant) for its database-wide generated artifacts, and per-file `Dependencies(aggregating = false, sourceFile)` only for outputs that depend on a single source.

Practical choice for a `Columns` generator:
- Use `aggregating = true` — the generated companion object for each entity is safe and correct even when entities are added/removed elsewhere in the project.
- Pass the entity's own `containingFile` as the sole source hint so KSP can at least skip regeneration when an unrelated file changes (in KSP2's smarter incremental engine).

---

### Q6 — Minimum KSP version compatible with Room 2.6

Room 2.6.x does not publish a hard minimum KSP API version requirement in its release notes, but the practical floor is determined by:

1. **KSP version scheme**: KSP versions are bound to Kotlin compiler versions in the format `<kotlin-version>-<ksp-patch>` (e.g. `1.9.20-1.0.14`). Room 2.6.x targets **Kotlin 1.9.x** — confirmed by the KSP gradle integration test suite which wires `room-compiler:2.6.1` against KSP in the `1.9.x` line.

2. **Minimum recommended**: **KSP `1.9.20-1.0.14`** (released with Kotlin 1.9.20). This is the earliest 1.9 stable release and the one used in KSP's own Room 2.6.1 integration tests (`GradleCompilationTest.kt`, google/ksp).

3. **KSP 2.x / Kotlin 2.0**: Room 2.6.x predates the Kotlin 2.0 requirement. Room 2.7.0 is where Kotlin 2.0 + KSP2 became mandatory. For Room 2.6.x, remain on the `1.9.x-1.0.x` KSP track.

4. **Latest stable on 1.9 track** (as of research date): `1.9.25-1.0.20`.

Concretely for a `build.gradle.kts` targeting Room 2.6.x:

```kotlin
plugins {
    id("com.google.devtools.ksp") version "1.9.24-1.0.20"  // any 1.9.20+ patch is fine
}
dependencies {
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-runtime:2.6.1")
}
```

Sources: KSP releases list (`gh api repos/google/ksp/releases`), KSP `GradleCompilationTest.kt` (google/ksp), Room release notes (developer.android.com/jetpack/androidx/releases/room#2.6.0).
