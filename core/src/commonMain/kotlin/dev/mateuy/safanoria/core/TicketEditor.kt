package dev.mateuy.safanoria.core

/** Frontmatter field order (SPEC §5). New fields are inserted at their place in it. */
public val FIELD_ORDER: List<String> = listOf(
    "id", "type", "title", "status", "priority", "size", "area", "assignee", "created", "updated",
    "parent", "childrenMergeInto", "blockedBy", "related", "refs", "requests", "resolvedIn",
)

public class TicketEditException(message: String) : Exception(message)

/**
 * Edits a ticket's text in place, touching only the lines an edit targets: field order, unknown
 * fields, comments, unknown sections, line endings and the final newline are kept (SPEC §5, §7).
 * Never re-serializes the YAML. An edit it can't make safely throws [TicketEditException].
 */
public class TicketEditor(original: String) {
    private val crlf = original.contains("\r\n")
    /** Lines without their `\r`; joined back with the file's line ending. */
    private val lines: MutableList<String> = original.split('\n').map { it.removeSuffix("\r") }.toMutableList()

    public val text: String get() = lines.joinToString(if (crlf) "\r\n" else "\n")

    // --- frontmatter --------------------------------------------------------------------------

    private fun frontmatterRange(): IntRange {
        val bounds = Frontmatter.bounds(lines) ?: throw TicketEditException("the file has no frontmatter block")
        return bounds.openLine until bounds.closeLine - 1 // indexes of the YAML lines
    }

    /** Index of the line `key:` at the top level of the frontmatter, or null. */
    private fun fieldIndex(key: String): Int? = frontmatterRange().firstOrNull { lines[it].startsWith("$key:") }

    /** Indexes of the lines that belong to the field at [index]: indented lines after it. */
    private fun fieldExtent(index: Int): IntRange {
        var end = index
        val range = frontmatterRange()
        while (end + 1 in range && (lines[end + 1].startsWith(" ") || lines[end + 1].startsWith("\t") || lines[end + 1].isBlank())) end++
        while (end > index && lines[end].isBlank()) end--
        return index..end
    }

    /** Where a missing field goes: after the last present field that precedes it in [FIELD_ORDER]. */
    private fun insertionIndex(key: String): Int {
        val before = FIELD_ORDER.takeWhile { it != key }
        val anchor = before.mapNotNull { fieldIndex(it) }.maxOrNull()
        return if (anchor != null) fieldExtent(anchor).last + 1 else frontmatterRange().first
    }

    /** Sets a top-level scalar field, e.g. `status`, `updated`, `parent`. */
    public fun setField(key: String, value: String?): TicketEditor = apply {
        val rendered = "$key: ${yamlScalar(value)}"
        val index = fieldIndex(key)
        if (index == null) {
            lines.add(insertionIndex(key), rendered)
            return@apply
        }
        val extent = fieldExtent(index)
        val current = lines[index].removePrefix("$key:")
        if (extent.last != index || current.trim().startsWith("|") || current.trim().startsWith(">")) {
            throw TicketEditException("$key spans several lines; edit it by hand")
        }
        lines[index] = rendered + trailingComment(current)
    }

    /** Sets a top-level list field in flow style, e.g. `blockedBy: [a, b]`. */
    public fun setList(key: String, values: List<String>): TicketEditor = apply {
        val rendered = "$key: [${values.joinToString(", ") { yamlScalar(it) }}]"
        val index = fieldIndex(key)
        if (index == null) {
            lines.add(insertionIndex(key), rendered)
        } else {
            val extent = fieldExtent(index)
            repeat(extent.last - extent.first) { lines.removeAt(index + 1) }
            lines[index] = rendered
        }
    }

    /**
     * Sets `key.sub` in a block map, e.g. `resolvedIn.app: 4.3.0`: adds the map when absent, turns
     * `key: null` (or `key:`) into a block, replaces or appends the entry.
     */
    public fun setMapEntry(key: String, sub: String, value: String?): TicketEditor = apply {
        val entry = "  $sub: ${yamlScalar(value)}"
        val index = fieldIndex(key)
        if (index == null) {
            val at = insertionIndex(key)
            lines.add(at, "$key:")
            lines.add(at + 1, entry)
            return@apply
        }
        val inline = lines[index].removePrefix("$key:")
        val inlineValue = inline.substringBefore(" #").trim()
        when {
            inlineValue == "" || inlineValue == "null" || inlineValue == "~" || inlineValue == "{}" -> {
                val extent = fieldExtent(index)
                if (extent.last == index) {
                    lines[index] = "$key:" + trailingComment(inline)
                    lines.add(index + 1, entry)
                    return@apply
                }
            }
            else -> throw TicketEditException("$key is written inline ($inlineValue); edit it by hand")
        }
        val extent = fieldExtent(index)
        val existing = ((index + 1)..extent.last).firstOrNull { lines[it].trimStart().startsWith("$sub:") }
        if (existing != null) {
            val indent = lines[existing].takeWhile { it == ' ' }
            lines[existing] = "$indent$sub: ${yamlScalar(value)}" + trailingComment(lines[existing].trimStart().removePrefix("$sub:"))
        } else {
            val indent = lines.getOrNull(index + 1)?.takeWhile { it == ' ' }?.ifEmpty { "  " } ?: "  "
            lines.add(extent.last + 1, "$indent$sub: ${yamlScalar(value)}")
        }
    }

    // --- body ---------------------------------------------------------------------------------

    /** Checks or unchecks the checklist item on file line [line] (1-based). */
    public fun setChecked(line: Int, checked: Boolean): TicketEditor = apply {
        val index = line - 1
        val current = lines.getOrNull(index)
        if (current == null || !CHECKBOX.containsMatchIn(current)) throw TicketEditException("line $line is not a checklist item")
        lines[index] = current.replaceFirst(CHECKBOX, if (checked) "- [x]" else "- [ ]")
    }

    /** Index of `## <name>` and the index after its section (the next heading or the end). */
    private fun sectionBounds(name: String): Pair<Int, Int> {
        val heading = lines.indexOfFirst { it == "## $name" }
        if (heading < 0) throw TicketEditException("the ticket has no '## $name' section")
        val end = ((heading + 1) until lines.size).firstOrNull { lines[it].startsWith("## ") } ?: lines.size
        return heading to end
    }

    /**
     * Replaces the content of `## <name>` with [text], keeping one blank line after the heading
     * and one before the next heading. For filling a template's placeholder (e.g. Objective).
     */
    public fun replaceSectionContent(name: String, text: String): TicketEditor = apply {
        val (heading, end) = sectionBounds(name)
        repeat(end - heading - 1) { lines.removeAt(heading + 1) }
        val content = listOf("") + text.trim('\n').split('\n') + if (heading + 1 < lines.size) listOf("") else emptyList()
        lines.addAll(heading + 1, content)
    }

    /**
     * Appends `- [ ] <text>` to `## Plan`: after its last checklist item (and that item's
     * continuation lines), else after its last content line, else after the heading.
     */
    public fun appendPlanItem(text: String, checked: Boolean = false): TicketEditor = apply {
        val (heading, end) = sectionBounds("Plan")
        val item = "- [${if (checked) "x" else " "}] $text"
        val lastItem = ((heading + 1) until end).lastOrNull { CHECKBOX.containsMatchIn(lines[it]) }
        val after = if (lastItem != null) {
            var i = lastItem
            while (i + 1 < end && lines[i + 1].isNotBlank() && (lines[i + 1].startsWith(" ") || lines[i + 1].startsWith("\t"))) i++
            i
        } else {
            ((heading + 1) until end).lastOrNull { lines[it].isNotBlank() }
        }
        if (after != null) {
            lines.add(after + 1, item)
        } else {
            // Empty section: blank line, item, and keep a blank line before the next heading.
            lines.add(heading + 1, "")
            lines.add(heading + 2, item)
            if (heading + 3 < lines.size && lines[heading + 3].isNotBlank()) lines.add(heading + 3, "")
        }
    }

    /** Appends `- **date** · ref · text` at the end of `## Work Log`; extra lines are indented. */
    public fun appendWorkLog(date: String, ref: String, text: String): TicketEditor = apply {
        val heading = lines.indexOfFirst { it == "## Work Log" }
        if (heading < 0) throw TicketEditException("the ticket has no '## Work Log' section")
        val sectionEnd = ((heading + 1) until lines.size).firstOrNull { lines[it].startsWith("## ") } ?: lines.size
        val lastContent = ((heading + 1) until sectionEnd).lastOrNull { lines[it].isNotBlank() }
        val entry = text.split('\n').mapIndexed { i, l -> if (i == 0) "- **$date** · $ref · $l" else "  $l" }
        if (lastContent == null) {
            // Empty section: one blank line after the heading, then the entry.
            val at = heading + 1
            if (lines.getOrNull(at)?.isBlank() != true || at >= sectionEnd) lines.add(at, "")
            lines.addAll(at + 1, entry)
        } else {
            lines.addAll(lastContent + 1, entry)
        }
    }

    private companion object {
        val CHECKBOX = Regex("^- \\[[ xX]]")

        /**
         * The ` # comment` after a plain (unquoted) value, with the spaces before it, so it is
         * kept as written when the value changes. Quoted values keep no comment (a `#` may be
         * inside the quotes; not worth parsing for a rare case).
         */
        fun trailingComment(afterKey: String): String {
            if (afterKey.trimStart().let { it.startsWith("\"") || it.startsWith("'") }) return ""
            val hash = Regex("\\s#").find(afterKey)?.range?.first ?: return ""
            val gapStart = afterKey.substring(0, hash + 1).trimEnd().length
            return afterKey.substring(gapStart)
        }

        private val PLAIN = Regex("^[A-Za-z0-9_./][A-Za-z0-9_./ -]*$")
        private val NOT_STRING = Regex("^(true|false|null|~|[-+]?[0-9]+(\\.[0-9]*)?([eE][-+]?[0-9]+)?|\\.[0-9]+)$")

        /** YAML for a string value: plain when unambiguous, else double-quoted. Null → `null`. */
        fun yamlScalar(value: String?): String = when {
            value == null -> "null"
            PLAIN.matches(value) && !NOT_STRING.matches(value) && !value.endsWith(" ") -> value
            else -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
        }
    }
}
