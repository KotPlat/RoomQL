package com.roomql.ksp

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

private const val ENTITY_ANNOTATION = "androidx.room.Entity"
private const val COLUMN_INFO_ANNOTATION = "androidx.room.ColumnInfo"
private val COLUMN_CLASS = ClassName("com.roomql.runtime", "Column")

class RoomQlProcessor(private val environment: SymbolProcessorEnvironment) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getSymbolsWithAnnotation(ENTITY_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .forEach { generateColumnsObject(it) }
        return emptyList()
    }

    private fun generateColumnsObject(classDecl: KSClassDeclaration) {
        val tableName = extractTableName(classDecl)
        val packageName = classDecl.packageName.asString()
        val className = classDecl.simpleName.asString()
        val objectName = "${className}Columns"

        val typeSpec = TypeSpec.objectBuilder(objectName)
            .apply {
                classDecl.getAllProperties().forEach { prop ->
                    val columnName = extractColumnName(prop)
                    val kspType = prop.type.resolve()
                    val typeName = kspType.toTypeName()
                    val columnType = COLUMN_CLASS.parameterizedBy(typeName)

                    addProperty(
                        PropertySpec.builder(prop.simpleName.asString(), columnType)
                            .initializer("%T(%S, %S)", COLUMN_CLASS, columnName, tableName)
                            .build()
                    )
                }
            }
            .build()

        FileSpec.builder(packageName, objectName)
            .addType(typeSpec)
            .build()
            .writeTo(environment.codeGenerator, aggregating = false)
    }

    private fun extractTableName(classDecl: KSClassDeclaration): String {
        val annotation = classDecl.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == ENTITY_ANNOTATION
        }
        val explicitName = annotation?.arguments
            ?.firstOrNull { it.name?.asString() == "tableName" }
            ?.value as? String

        return if (explicitName.isNullOrEmpty()) classDecl.simpleName.asString() else explicitName
    }

    private fun extractColumnName(prop: KSPropertyDeclaration): String {
        val annotation = prop.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == COLUMN_INFO_ANNOTATION
        }
        val explicitName = annotation?.arguments
            ?.firstOrNull { it.name?.asString() == "name" }
            ?.value as? String

        return if (explicitName.isNullOrEmpty()) prop.simpleName.asString() else explicitName
    }
}

class RoomQlProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        RoomQlProcessor(environment)
}
