package com.roomql.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

private const val ENTITY_ANNOTATION = "androidx.room.Entity"
private const val COLUMN_INFO_ANNOTATION = "androidx.room.ColumnInfo"
private const val IGNORE_ANNOTATION = "androidx.room.Ignore"
private const val TABLE_SUFFIX_OPTION = "roomql.tableSuffix"
private const val DEFAULT_TABLE_SUFFIX = "Table"
private val COLUMN_CLASS = ClassName("com.roomql.runtime", "Column")
private val ENTITY_TABLE_CLASS = ClassName("com.roomql.runtime", "EntityTable")

internal class RoomQlProcessor(private val environment: SymbolProcessorEnvironment) : SymbolProcessor {
    private val tableSuffix = environment.options[TABLE_SUFFIX_OPTION] ?: DEFAULT_TABLE_SUFFIX

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val deferred = mutableListOf<KSAnnotated>()
        resolver.getSymbolsWithAnnotation(ENTITY_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .forEach { classDecl ->
                if (classDecl.validate()) generateTableObject(classDecl)
                else deferred.add(classDecl)
            }
        return deferred
    }

    private fun generateTableObject(classDecl: KSClassDeclaration) {
        val tableName = extractTableName(classDecl)
        val packageName = classDecl.packageName.asString()
        val objectName = "${classDecl.simpleName.asString()}$tableSuffix"

        val props = classDecl.getAllProperties().filterNot { it.hasAnnotation(IGNORE_ANNOTATION) }.toList()
        val columnNames = props.map { extractColumnName(it) }

        val typeSpec = TypeSpec.objectBuilder(objectName)
            .addSuperinterface(ENTITY_TABLE_CLASS)
            .apply {
                classDecl.containingFile?.let { addOriginatingKSFile(it) }
                addProperty(
                    PropertySpec.builder("tableName", String::class, KModifier.OVERRIDE)
                        .initializer("%S", tableName)
                        .build()
                )
                addProperty(
                    PropertySpec.builder("allColumnNames", List::class.asClassName().parameterizedBy(String::class.asClassName()), KModifier.OVERRIDE)
                        .initializer("listOf(${columnNames.joinToString(", ") { "%S" }})", *columnNames.toTypedArray())
                        .build()
                )
                props.forEach { prop ->
                    val columnType = COLUMN_CLASS.parameterizedBy(prop.type.resolve().toTypeName())
                    addProperty(
                        PropertySpec.builder(prop.simpleName.asString(), columnType)
                            .initializer("%T(%S, %S)", COLUMN_CLASS, extractColumnName(prop), tableName)
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
        val explicit = findAnnotationArg(classDecl.annotations, ENTITY_ANNOTATION, "tableName")
        return if (explicit.isNullOrEmpty()) classDecl.simpleName.asString() else explicit
    }

    private fun extractColumnName(prop: KSPropertyDeclaration): String {
        val explicit = findAnnotationArg(prop.annotations, COLUMN_INFO_ANNOTATION, "name")
        return if (explicit.isNullOrEmpty()) prop.simpleName.asString() else explicit
    }

    private fun findAnnotationArg(
        annotations: Sequence<KSAnnotation>,
        annotationFqn: String,
        argName: String,
    ): String? {
        val annotation = annotations
            .firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == annotationFqn }
            ?: return null
        val value = annotation.arguments.firstOrNull { it.name?.asString() == argName }?.value ?: return null
        if (value !is String) {
            environment.logger.error(
                "RoomQL: expected a String for @$annotationFqn's '$argName' but got ${value::class.simpleName}",
                annotation,
            )
            return null
        }
        return value
    }
}

private fun KSPropertyDeclaration.hasAnnotation(annotationFqn: String): Boolean =
    annotations.any { it.annotationType.resolve().declaration.qualifiedName?.asString() == annotationFqn }

public class RoomQlProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        RoomQlProcessor(environment)
}
