import com.charleskorn.kaml.Yaml
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
import okio.Path

val ticketSchema = """
{
  "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "required": ["id", "type", "title", "status", "priority", "size", "created", "updated"],
  "properties": {
    "id": { "type": "string", "pattern": "^[a-z][a-z0-9-]{2,39}${'$'}" },
    "type": { "enum": ["feature", "bug", "maintenance", "research"] },
    "title": { "type": "string" },
    "status": { "enum": ["backlog", "ready", "in-progress", "review", "done", "wontfix"] },
    "priority": { "enum": ["low", "medium", "high", "urgent"] },
    "size": { "enum": ["XS", "S", "M", "L", "XL"] },
    "created": { "type": "string", "pattern": "^\\d{4}-\\d{2}-\\d{2}${'$'}" },
    "updated": { "type": "string", "pattern": "^\\d{4}-\\d{2}-\\d{2}${'$'}" },
    "parent": { "type": ["string", "null"] },
    "blockedBy": { "type": "array", "items": { "type": "string" } },
    "related": { "type": "array", "items": { "type": "string" } }
  }
}
""".trimIndent()

/** YAML scalars are untyped in kaml; type them like YAML 1.2 core schema would. */
fun YamlNode.toJson(): JsonElement = when (this) {
    is YamlMap -> JsonObject(entries.entries.associate { (k, v) -> k.content to v.toJson() })
    is YamlList -> JsonArray(items.map { it.toJson() })
    is YamlNull -> JsonNull
    is YamlTaggedNode -> innerNode.toJson()
    is YamlScalar -> content.toLongOrNull()?.let(::JsonPrimitive)
        ?: content.toBooleanStrictOrNull()?.let(::JsonPrimitive)
        ?: JsonPrimitive(content)
}

/** File line of the node at a JSON pointer like `/blockedBy/0`, or of the map when absent. */
fun YamlNode.lineOf(pointer: String, offset: Int): Int {
    var node: YamlNode = this
    for (segment in pointer.split('/').drop(1).filter { it.isNotEmpty() }) {
        node = when (val n = node) {
            is YamlMap -> n.entries.entries.firstOrNull { it.key.content == segment }?.let { it.key }
                ?: return n.location.line + offset - 1
            is YamlList -> n.items.getOrNull(segment.toInt()) ?: return n.location.line + offset - 1
            else -> return n.location.line + offset - 1
        }
    }
    return node.location.line + offset - 1
}

fun schemaCheck(root: Path) {
    val schema = JsonSchema.fromDefinition(ticketSchema)
    val yaml = Yaml.default
    val files = fileSystem.list(root / "tickets").filter { it.name.endsWith(".md") }
    for (file in files) {
        val (fm, offset) = frontmatter(fileSystem.read(file) { readUtf8() }) ?: continue
        val errors = mutableListOf<ValidationError>()
        schema.validate(yaml.parseToYamlNode(fm).toJson(), errors::add)
        println("${file.name}: ${if (errors.isEmpty()) "valid" else "${errors.size} errors"}")
    }

    val broken = """
        id: Bad--Id
        type: feature
        status: almost
        priority: high
        size: M
        created: 2026-1-1
        updated: 2026-10-01
        blockedBy: [ok-id, 42]
    """.trimIndent()
    val node = yaml.parseToYamlNode(broken)
    val errors = mutableListOf<ValidationError>()
    schema.validate(node.toJson(), errors::add)
    for (e in errors) {
        println("line ${node.lineOf(e.objectPath.toString(), 2)}: ${e.objectPath} ${e.message}")
    }
}
