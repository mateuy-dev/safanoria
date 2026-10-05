package dev.mateuy.safanoria.core

import com.charleskorn.kaml.YamlNode
import okio.Path

/** A value and where it is in its file (1-based). */
public data class Located<out T>(val value: T, val line: Int, val column: Int)

public enum class TicketType { FEATURE, BUG, MAINTENANCE, RESEARCH }
public enum class Status { BACKLOG, READY, IN_PROGRESS, REVIEW, DONE, WONTFIX }
public enum class Priority { VERY_LOW, LOW, MEDIUM, HIGH, URGENT }
public enum class Size { XS, S, M, L, XL }

/** `in-progress` → `IN_PROGRESS`; null for values outside the enum (the schema reports those). */
internal inline fun <reified E : Enum<E>> enumOf(text: String?): E? =
    enumValues<E>().firstOrNull { it.name == text?.uppercase()?.replace('-', '_') }

/** The spec spelling of an enum value: `IN_PROGRESS` → `in-progress`, `XL` → `XL`. */
public val Enum<*>.text: String
    get() = if (this is Size) name else name.lowercase().replace('_', '-')

public data class Request(val user: String?, val who: String?, val channel: String?, val date: String?, val line: Int)

/**
 * Where the frontmatter is in the file. Lines are 1-based: [openLine] and [closeLine] are the
 * `---` lines; the YAML is between them, the body starts after [closeLine].
 */
public data class FrontmatterBounds(val openLine: Int, val closeLine: Int)

/** The ticket frontmatter (SPEC §5): typed fields with their lines, read leniently. */
public class Frontmatter internal constructor(internal val block: YamlBlock) {
    private fun scalar(key: String): Located<String>? = block.root.entry(key)?.let { (_, v) ->
        v.text()?.let { Located(it, block.lineOf(v), block.columnOf(v)) }
    }

    private fun idList(key: String): List<Located<String>> =
        items(block.root.get(key)).mapNotNull { n -> n.text()?.let { Located(it, block.lineOf(n), block.columnOf(n)) } }

    private fun items(node: YamlNode?): List<YamlNode> =
        (node?.unwrap() as? com.charleskorn.kaml.YamlList)?.items ?: emptyList()

    public val id: Located<String>? get() = scalar("id")
    public val title: Located<String>? get() = scalar("title")
    public val type: TicketType? get() = enumOf<TicketType>(scalar("type")?.value)
    public val status: Status? get() = enumOf<Status>(scalar("status")?.value)
    public val priority: Priority? get() = enumOf<Priority>(scalar("priority")?.value)
    public val size: Size? get() = enumOf<Size>(scalar("size")?.value)
    public val created: Located<String>? get() = scalar("created")
    public val updated: Located<String>? get() = scalar("updated")
    public val assignee: Located<String>? get() = scalar("assignee")
    public val parent: Located<String>? get() = scalar("parent")
    public val childrenMergeInto: String? get() = scalar("childrenMergeInto")?.value
    public val area: List<Located<String>> get() = idList("area")
    public val tags: List<Located<String>> get() = idList("tags")
    public val blockedBy: List<Located<String>> get() = idList("blockedBy")
    public val related: List<Located<String>> get() = idList("related")

    public val refs: Map<String, List<String>>
        get() = block.root.get("refs").mapEntries().associate { (k, v) -> k.content to (v.textList() ?: emptyList()) }

    public val requests: List<Request>
        get() = items(block.root.get("requests")).map { r ->
            Request(r.get("user").text(), r.get("who").text(), r.get("channel").text(), r.get("date").text(), block.lineOf(r))
        }

    /** `resolvedIn`, or null when absent or null. A component's value is null until released. */
    public val resolvedIn: Map<String, Located<String?>>?
        get() {
            val node = block.root.get("resolvedIn")
            if (node.isNull()) return null
            return node.mapEntries().associate { (k, v) -> k.content to Located(v.text(), block.lineOf(k), block.columnOf(k)) }
        }

    /** Top-level keys in file order, with their lines; includes unknown fields. */
    public val keys: List<Located<String>>
        get() = block.root.mapEntries().map { (k, _) -> Located(k.content, block.lineOf(k), block.columnOf(k)) }

    /** Schema violations (SPEC §5 single-file rules), as diagnostics with file lines. */
    public fun schemaDiagnostics(): List<Diagnostic> =
        SchemaValidator.ticketErrors(block).map { SchemaValidator.toDiagnostic(block, it) }

    internal fun schemaErrors(): List<SchemaError> = SchemaValidator.ticketErrors(block)

    public companion object {
        /** Finds the `---` lines. Null when the file doesn't start with a frontmatter block. */
        public fun bounds(lines: List<String>): FrontmatterBounds? {
            if (lines.firstOrNull()?.trimEnd('\r') != "---") return null
            val close = (1 until lines.size).firstOrNull { lines[it].trimEnd('\r') == "---" } ?: return null
            return FrontmatterBounds(openLine = 1, closeLine = close + 1)
        }

        /** Parses the frontmatter of [text]: the frontmatter, or the diagnostics that prevent it. */
        public fun parse(file: Path?, text: String): Pair<Frontmatter?, List<Diagnostic>> {
            val lines = text.split('\n')
            val bounds = bounds(lines)
                ?: return null to listOf(Diagnostic(file, 1, null, "frontmatter-missing", "the file must start with a '---' frontmatter block"))
            val yamlText = lines.subList(bounds.openLine, bounds.closeLine - 1).joinToString("\n") { it.trimEnd('\r') }
            val (block, error) = parseYamlBlock(file, yamlText, firstLine = bounds.openLine + 1)
            return if (block == null) null to listOfNotNull(error) else Frontmatter(block) to emptyList()
        }
    }
}
