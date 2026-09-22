package com.roomql.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

private const val ENTITY_ANNOTATION = "androidx.room.Entity"
private const val COLUMN_INFO_ANNOTATION = "androidx.room.ColumnInfo"
private const val IGNORE_ANNOTATION = "androidx.room.Ignore"
private const val PROJECTION_ANNOTATION = "com.roomql.runtime.Projection"
private const val TABLE_SUFFIX_OPTION = "roomql.tableSuffix"
private const val DEFAULT_TABLE_SUFFIX = "Table"
private val COLUMN_CLASS = ClassName("com.roomql.runtime", "Column")
private val ENTITY_TABLE_CLASS = ClassName("com.roomql.runtime", "EntityTable")
private val EXPRESSION_CLASS = ClassName("com.roomql.runtime", "Expression")
private val SELECT_ITEM_CLASS = ClassName("com.roomql.runtime", "SelectItem")
private val ALIAS_MEMBER = MemberName("com.roomql.runtime", "alias")

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
        resolver.getSymbolsWithAnnotation(PROJECTION_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .forEach { classDecl ->
                if (classDecl.validate()) generateProjectionFactory(classDecl)
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

    /**
     * Generates `<ClassName>Projection(...)`: one `Expression<T>` parameter per primary
     * constructor property, in declaration order, returning the aliased expressions ready
     * to spread into `select(...)`. A missing argument is then a plain Kotlin compile error
     * at the call site — the same mechanism as forgetting a constructor argument.
     */
    private fun generateProjectionFactory(classDecl: KSClassDeclaration) {
        val packageName = classDecl.packageName.asString()
        val className = classDecl.simpleName.asString()
        val functionName = "${className}Projection"

        val params = classDecl.primaryConstructor?.parameters.orEmpty()
        if (params.isEmpty()) {
            environment.logger.error(
                "RoomQL: @Projection class '$className' has no primary constructor properties to project",
                classDecl,
            )
            return
        }

        // Room's @ColumnInfo doesn't target constructor parameters, so Kotlin attaches it to
        // the backing property instead of the KSValueParameter — look the property up by name.
        val propertiesByName = classDecl.getAllProperties().associateBy { it.simpleName.asString() }
        val fields = params.map { param ->
            val property = propertiesByName[param.name?.asString()]
            val columnName = if (property != null) extractColumnName(property) else extractColumnName(param)
            param to columnName
        }
        val returnType = ARRAY.parameterizedBy(SELECT_ITEM_CLASS.parameterizedBy(STAR))

        val body = CodeBlock.builder().apply {
            add("return arrayOf(\n")
            indent()
            fields.forEachIndexed { i, (param, columnName) ->
                add("%N %M %S", param.name!!.asString(), ALIAS_MEMBER, columnName)
                add(if (i < fields.lastIndex) ",\n" else "\n")
            }
            unindent()
            add(")\n")
        }.build()

        val funSpec = FunSpec.builder(functionName)
            .apply {
                fields.forEach { (param, _) ->
                    val paramName = param.name!!.asString()
                    val paramType = EXPRESSION_CLASS.parameterizedBy(param.type.resolve().toTypeName())
                    addParameter(ParameterSpec.builder(paramName, paramType).build())
                }
            }
            .returns(returnType)
            .addCode(body)
            .build()

        FileSpec.builder(packageName, functionName)
            .addFunction(funSpec)
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

    private fun extractColumnName(param: KSValueParameter): String {
        val explicit = findAnnotationArg(param.annotations, COLUMN_INFO_ANNOTATION, "name")
        return if (explicit.isNullOrEmpty()) param.name!!.asString() else explicit
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

/**
 * KSP's entry point into RoomQL's code generator. Registered through `META-INF/services`, so
 * applying the `com.google.devtools.ksp` plugin and adding `ksp("io.github.kotplat.roomql:ksp-processor:...")`
 * is all a consumer needs — you never reference this type directly.
 *
 * The processor it creates does two things, driven by annotations on your own source:
 * - For every `@Entity`-annotated class, generates `object <EntityName>Table : EntityTable`
 *   (suffix configurable via the `roomql.tableSuffix` option) in the same package, with a
 *   typed [Column][com.roomql.runtime.Column] per property. Honours `@Entity(tableName = ...)`,
 *   `@ColumnInfo(name = ...)`, and skips `@Ignore`d properties.
 * - For every `@Projection`-annotated (`com.roomql.runtime.Projection`) data class, generates
 *   `<ClassName>Projection(...)` in the same package — one `Expression<T>` parameter per
 *   primary-constructor property, aliased from `@ColumnInfo(name = ...)` — to spread into
 *   `select(...)`.
 *
 * A class whose symbols aren't yet resolvable in the current round (`validate()` returns
 * `false`) is deferred to a later KSP processing round rather than generated incorrectly or
 * skipped.
 */
public class RoomQlProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        RoomQlProcessor(environment)
}
