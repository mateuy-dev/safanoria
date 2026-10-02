package dev.mateuy.safanoria.core

import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.YamlTaggedNode
import io.github.optimumcode.json.schema.JsonSchema
import io.github.optimumcode.json.schema.ValidationError
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A schema violation: [keyword] is the failing schema keyword, [pointer] the JSON pointer. */
internal data class SchemaError(val pointer: String, val keyword: String, val message: String, val line: Int, val column: Int)

/** Checks YAML blocks against the embedded schemas (`schema/` in the repository). */
internal object SchemaValidator {
    private val ticket by lazy { JsonSchema.fromDefinition(Embedded.TICKET_SCHEMA) }
    private val config by lazy { JsonSchema.fromDefinition(Embedded.CONFIG_SCHEMA) }

    fun ticketErrors(block: YamlBlock): List<SchemaError> = errors(ticket, block)
    fun configErrors(block: YamlBlock): List<SchemaError> = errors(config, block)

    fun toDiagnostic(block: YamlBlock, e: SchemaError): Diagnostic {
        val where = e.pointer.ifEmpty { "frontmatter" }
        return Diagnostic(block.file, e.line, e.column, "schema-${e.keyword}", "$where: ${e.message}")
    }

    private fun errors(schema: JsonSchema, block: YamlBlock): List<SchemaError> {
        val found = mutableListOf<ValidationError>()
        schema.validate(toJson(block, block.root), found::add)
        return found.map { e ->
            val pointer = e.objectPath.toString()
            val node = nodeAt(block.root, pointer)
            val keyword = e.schemaPath.toString().substringAfterLast('/')
            SchemaError(pointer, keyword, e.message, block.lineOf(node), block.columnOf(node))
        }
    }

    /**
     * YAML to JSON with YAML 1.2 core-schema typing: plain `12` is a number, `true` a boolean,
     * `4.3` a number; quoted and block scalars, and everything else, stay strings. Dates stay
     * strings (SPEC §4).
     */
    fun toJson(block: YamlBlock, node: YamlNode): JsonElement = when (node) {
        is YamlTaggedNode -> toJson(block, node.innerNode)
        is YamlNull -> JsonNull
        is YamlMap -> JsonObject(node.entries.entries.associate { (k, v) -> k.content to toJson(block, v) })
        is YamlList -> JsonArray(node.items.map { toJson(block, it) })
        is YamlScalar -> {
            val text = node.content
            when {
                block.isExplicitString(node) -> JsonPrimitive(text)
                INTEGER.matches(text) -> text.toLongOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(text)
                FLOAT.matches(text) -> JsonPrimitive(text.toDouble())
                text == "true" || text == "false" -> JsonPrimitive(text == "true")
                else -> JsonPrimitive(text)
            }
        }
    }

    private val INTEGER = Regex("[-+]?[0-9]+")
    private val FLOAT = Regex("[-+]?(\\.[0-9]+|[0-9]+(\\.[0-9]*)?)([eE][-+]?[0-9]+)?")

    /** The node at a JSON pointer, or the deepest existing parent (a missing property's map). */
    private fun nodeAt(root: YamlNode, pointer: String): YamlNode {
        var current = root   // where the walk continues
        var located = root   // what the error points at
        for (segment in pointer.split('/').drop(1).filter { it.isNotEmpty() }) {
            val key = segment.replace("~1", "/").replace("~0", "~")
            when (val n = current.unwrap()) {
                is YamlMap -> {
                    val (k, v) = n.entries.entries.firstOrNull { it.key.content == key } ?: return located
                    current = v
                    // A map or list value starts on the next line; the key's line is the field's.
                    located = if (v.unwrap() is YamlMap || v.unwrap() is YamlList) k else v
                }
                is YamlList -> {
                    current = n.items.getOrNull(key.toIntOrNull() ?: -1) ?: return located
                    located = current
                }
                else -> return located
            }
        }
        return located
    }
}
