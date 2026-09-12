@file:OptIn(ExperimentalCompilerApi::class)

package com.roomql.ksp

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspSourcesDir
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoomQlProcessorTest {

    private fun compile(vararg sources: SourceFile): Pair<JvmCompilationResult, KotlinCompilation> {
        val compilation = KotlinCompilation().apply {
            this.sources = sources.toList()
            configureKsp(useKsp2 = true) {
                symbolProcessorProviders += RoomQlProcessorProvider()
            }
            inheritClassPath = true
            messageOutputStream = System.out
        }
        return compilation.compile() to compilation
    }

    private fun findGeneratedFile(compilation: KotlinCompilation, name: String): File =
        compilation.kspSourcesDir.walkTopDown()
            .filter { it.isFile && it.name == name }
            .first()

    // --- basic entity with explicit tableName ---

    @Test
    fun `generates columns object with explicit tableName`() {
        val entity = SourceFile.kotlin(
            "UserEntity.kt", """
            package test
            import androidx.room.Entity

            @Entity(tableName = "users")
            data class UserEntity(
                val id: Int,
                val name: String,
                val age: Int
            )
        """
        )

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
    fun `generates columns object using class name as default tableName`() {
        val entity = SourceFile.kotlin(
            "ProductEntity.kt", """
            package test
            import androidx.room.Entity

            @Entity
            data class ProductEntity(
                val id: Long,
                val title: String
            )
        """
        )

        val (result, compilation) = compile(entity)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generated = findGeneratedFile(compilation, "ProductEntityTable.kt").readText()
        assertTrue("ProductEntityTable" in generated)
        assertTrue(""""ProductEntity"""" in generated)
    }

    // --- @ColumnInfo(name = ...) overrides property name ---

    @Test
    fun `respects ColumnInfo name for column name`() {
        val entity = SourceFile.kotlin(
            "OrderEntity.kt", """
            package test
            import androidx.room.Entity
            import androidx.room.ColumnInfo

            @Entity(tableName = "orders")
            data class OrderEntity(
                val id: Int,
                @ColumnInfo(name = "created_at") val createdAt: Long
            )
        """
        )

        val (result, compilation) = compile(entity)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generated = findGeneratedFile(compilation, "OrderEntityTable.kt").readText()
        assertTrue(""""created_at"""" in generated)
        assertTrue("createdAt" in generated)
    }

    // --- nullable types ---

    @Test
    fun `nullable properties produce nullable Column type`() {
        val entity = SourceFile.kotlin(
            "NullableEntity.kt", """
            package test
            import androidx.room.Entity

            @Entity(tableName = "items")
            data class NullableEntity(
                val id: Int,
                val email: String?
            )
        """
        )

        val (result, compilation) = compile(entity)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generated = findGeneratedFile(compilation, "NullableEntityTable.kt").readText()
        assertTrue("String?" in generated)
        assertTrue("Column<Int>" in generated)
    }

    // --- package preserved in generated file ---

    @Test
    fun `generated file is in same package as entity`() {
        val entity = SourceFile.kotlin(
            "RoomEntity.kt", """
            package com.example.data
            import androidx.room.Entity

            @Entity(tableName = "rooms")
            data class RoomEntity(
                val id: Int
            )
        """
        )

        val (result, compilation) = compile(entity)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generated = findGeneratedFile(compilation, "RoomEntityTable.kt").readText()
        assertTrue(generated.contains("package com.example.data") || generated.contains("package com.example.`data`"))
    }

    // --- @Ignore ---

    @Test
    fun `Ignore'd properties are excluded from the generated columns and allColumnNames`() {
        val entity = SourceFile.kotlin(
            "UserEntity.kt", """
            package test
            import androidx.room.Entity
            import androidx.room.Ignore

            @Entity(tableName = "users")
            data class UserEntity(
                val id: Int,
                val name: String,
                @Ignore val fullNameCache: String = ""
            )
        """
        )

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
        val entity = SourceFile.kotlin(
            "ItemEntity.kt", """
            package test
            import androidx.room.Entity
            import androidx.room.ColumnInfo

            @Entity(tableName = "items")
            data class ItemEntity(
                val id: Int,
                @ColumnInfo(name = "item_name") val name: String
            )
        """
        )

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
        val source = SourceFile.kotlin(
            "Entities.kt", """
            package test
            import androidx.room.Entity

            @Entity(tableName = "users")
            data class UserEntity(val id: Int)

            @Entity(tableName = "posts")
            data class PostEntity(val id: Int, val title: String)
        """
        )

        val (result, compilation) = compile(source)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generatedNames = compilation.kspSourcesDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.name }
            .toSet()

        assertTrue("UserEntityTable.kt" in generatedNames)
        assertTrue("PostEntityTable.kt" in generatedNames)
    }
}
