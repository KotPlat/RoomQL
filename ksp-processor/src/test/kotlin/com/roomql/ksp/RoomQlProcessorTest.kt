@file:OptIn(ExperimentalCompilerApi::class)

package com.roomql.ksp

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.kspSourcesDir
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoomQlProcessorTest {
    private fun fixture(fileName: String): SourceFile {
        val resource = javaClass.classLoader.getResource("fixtures/$fileName")
            ?: error("Fixture not found: fixtures/$fileName")
        return SourceFile.kotlin(fileName, resource.readText())
    }

    private fun compile(
        vararg sources: SourceFile,
        processorOptions: Map<String, String> = emptyMap(),
    ): Pair<JvmCompilationResult, KotlinCompilation> {
        val compilation = KotlinCompilation().apply {
            this.sources = sources.toList()
            configureKsp(useKsp2 = true) {
                symbolProcessorProviders += RoomQlProcessorProvider()
            }
            kspProcessorOptions.putAll(processorOptions)
            inheritClassPath = true
            messageOutputStream = System.out
        }
        return compilation.compile() to compilation
    }

    private fun findGeneratedFile(compilation: KotlinCompilation, name: String): File =
        compilation.kspSourcesDir.walkTopDown().first { it.isFile && it.name == name }

    // --- basic entity with explicit tableName ---

    @Test
    fun `generates table object with explicit tableName`() {
        val entity = fixture("UserEntity.kt")

        val (result, compilation) = compile(entity)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generated = findGeneratedFile(compilation, "UserEntityTable.kt").readText()
        assertTrue("UserEntityTable" in generated)
        assertTrue(""""id", "users"""" in generated || """"id","users"""" in generated.replace(" ", ""))
        assertTrue(""""name", "users"""" in generated || """"name","users"""" in generated.replace(" ", ""))
        assertTrue(""""age", "users"""" in generated || """"age","users"""" in generated.replace(" ", ""))
    }

    // --- entity with default tableName (uses class name) ---

    @Test
    fun `generates table object using class name as default tableName`() {
        val entity = fixture("ProductEntity.kt")

        val (result, compilation) = compile(entity)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generated = findGeneratedFile(compilation, "ProductEntityTable.kt").readText()
        assertTrue("ProductEntityTable" in generated)
        assertTrue(""""ProductEntity"""" in generated)
    }

    // --- @ColumnInfo(name = ...) overrides property name ---

    @Test
    fun `respects ColumnInfo name for column name`() {
        val entity = fixture("OrderEntity.kt")

        val (result, compilation) = compile(entity)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generated = findGeneratedFile(compilation, "OrderEntityTable.kt").readText()
        assertTrue(""""created_at"""" in generated)
        assertTrue("createdAt" in generated)
    }

    // --- nullable types ---

    @Test
    fun `nullable properties produce nullable Column type`() {
        val entity = fixture("NullableEntity.kt")

        val (result, compilation) = compile(entity)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generated = findGeneratedFile(compilation, "NullableEntityTable.kt").readText()
        assertTrue("String?" in generated)
        assertTrue("Column<Int>" in generated)
    }

    // --- package preserved in generated file ---

    @Test
    fun `generated file is in same package as entity`() {
        val entity = fixture("RoomEntity.kt")

        val (result, compilation) = compile(entity)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generated = findGeneratedFile(compilation, "RoomEntityTable.kt").readText()
        assertTrue(generated.contains("package com.example.data") || generated.contains("package com.example.`data`"))
    }

    // --- configurable *Table suffix ---

    @Test
    fun `roomql tableSuffix option overrides the generated object name suffix`() {
        val entity = fixture("UserEntitySuffix.kt")

        val (result, compilation) = compile(entity, processorOptions = mapOf("roomql.tableSuffix" to "Cols"))

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        findGeneratedFile(compilation, "UserEntityCols.kt")
    }

    // --- @Ignore ---

    @Test
    fun `Ignore'd properties are excluded from the generated columns and allColumnNames`() {
        val entity = fixture("UserEntityIgnore.kt")

        val (result, compilation) = compile(entity)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generated = findGeneratedFile(compilation, "UserEntityTable.kt").readText()
        assertTrue("val id" in generated)
        assertTrue("val name" in generated)
        assertTrue("fullNameCache" !in generated)
        assertTrue(""""id"""" in generated)
        assertTrue(""""name"""" in generated)
    }

    // --- generated object implements EntityTable ---

    @Test
    fun `generated object implements EntityTable with tableName and allColumnNames`() {
        val entity = fixture("ItemEntity.kt")

        val (result, compilation) = compile(entity)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generated = findGeneratedFile(compilation, "ItemEntityTable.kt").readText()
        assertTrue("EntityTable" in generated)
        assertTrue(""""items"""" in generated)
        assertTrue("allColumnNames" in generated)
        assertTrue(""""id"""" in generated)
        assertTrue(""""item_name"""" in generated)
    }

    // --- multiple entities generate multiple files ---

    @Test
    fun `multiple entities each produce their own columns file`() {
        val source = fixture("Entities.kt")

        val (result, compilation) = compile(source)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generatedNames = compilation.kspSourcesDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.name }
            .toSet()

        assertTrue("UserEntityTable.kt" in generatedNames)
        assertTrue("PostEntityTable.kt" in generatedNames)
    }

    // --- @Projection: generates a typed factory function ---

    @Test
    fun `generates a projection factory with one Expression parameter per constructor property`() {
        val projection = fixture("CategorySummary.kt")

        val (result, compilation) = compile(projection)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generated = findGeneratedFile(compilation, "CategorySummaryProjection.kt").readText()
        assertTrue("fun CategorySummaryProjection(" in generated)
        assertTrue("categoryId: Expression<Int>" in generated)
        assertTrue("categoryName: Expression<String>" in generated)
        assertTrue("productCount: Expression<Long>" in generated)
        assertTrue("Array<SelectItem<*>>" in generated)
    }

    @Test
    fun `projection factory aliases each expression to its ColumnInfo name, defaulting to the property name`() {
        val projection = fixture("CategorySummary.kt")

        val (result, compilation) = compile(projection)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generated = findGeneratedFile(compilation, "CategorySummaryProjection.kt").readText()
        assertTrue("categoryId alias \"category_id\"" in generated)
        assertTrue("categoryName alias \"category_name\"" in generated)
        // No @ColumnInfo on productCount, so it falls back to the property's own name.
        assertTrue("productCount alias \"productCount\"" in generated)
    }

    @Test
    fun `a full call to the generated projection factory compiles`() {
        val projection = fixture("CategorySummary.kt")
        val caller = SourceFile.kotlin(
            "UseProjection.kt",
            """
            package test
            import com.roomql.runtime.Column

            fun useProjection() {
                val id = Column<Int>("category_id", "categories")
                val name = Column<String>("category_name", "categories")
                val count = Column<Long>("product_count", "categories")
                val projected = CategorySummaryProjection(id, name, count)
            }
            """.trimIndent(),
        )

        val (result, _) = compile(projection, caller)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    @Test
    fun `a call missing an argument to the generated projection factory fails to compile`() {
        val projection = fixture("CategorySummary.kt")
        val caller = SourceFile.kotlin(
            "UseProjection.kt",
            """
            package test
            import com.roomql.runtime.Column

            fun useProjection() {
                val id = Column<Int>("category_id", "categories")
                val name = Column<String>("category_name", "categories")
                val projected = CategorySummaryProjection(id, name)
            }
            """.trimIndent(),
        )

        val (result, _) = compile(projection, caller)

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
    }

    @Test
    fun `a call with a mismatched Expression type fails to compile`() {
        val projection = fixture("CategorySummary.kt")
        val caller = SourceFile.kotlin(
            "UseProjection.kt",
            """
            package test
            import com.roomql.runtime.Column

            fun useProjection() {
                val id = Column<Int>("category_id", "categories")
                val name = Column<String>("category_name", "categories")
                // productCount wants Expression<Long>, not Expression<Int>.
                val projected = CategorySummaryProjection(id, name, id)
            }
            """.trimIndent(),
        )

        val (result, _) = compile(projection, caller)

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
    }
}
