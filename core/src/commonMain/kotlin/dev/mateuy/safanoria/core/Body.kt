package dev.mateuy.safanoria.core

import okio.Path

/** The sections SPEC §7 defines, in their required order. */
public val STANDARD_SECTIONS: List<String> =
    listOf("Objective", "User Requests", "Acceptance Criteria", "Plan", "Design", "Learnings", "Work Log")

/**
 * A `## ` section. Lines are 1-based file lines: [headingLine] is the heading, the content is
 * [firstLine]..[lastLine] (empty when `lastLine < firstLine`).
 */
public data class Section(val name: String, val headingLine: Int, val firstLine: Int, val lastLine: Int)

/** `- [ ]` / `- [x]` item. [lastLine] includes its indented continuation lines. */
public data class CheckItem(
    val checked: Boolean,
    /** The text of the first line, after the checkbox. */
    val text: String,
    val line: Int,
    val lastLine: Int,
    /** In a Plan, the ticket id when the item starts with it in backticks (SPEC §7.5). */
    val childId: String?,
)

public sealed interface Resolution {
    public data class Promoted(val targets: String) : Resolution
    public data class NewTicket(val id: String) : Resolution
    public data object TicketOnly : Resolution
    public data object Pending : Resolution
}

public data class Learning(val text: String, val line: Int, val lastLine: Int, val resolution: Resolution, val resolutionLine: Int?)

public data class WorkLogEntry(val date: String, val ref: String, val text: String, val line: Int, val lastLine: Int)

public data class Quote(val text: String, val line: Int, val ref: String, val channel: String, val date: String, val attributionLine: Int)

/** The ticket body (SPEC §7), parsed from the lines after the frontmatter. */
public class Body internal constructor(
    private val file: Path?,
    private val lines: List<String>,
    /** File line of `lines[0]`. */
    private val firstLine: Int,
) {
    private val diagnosticList = mutableListOf<Diagnostic>()

    /** Malformed Work Log entries, Learnings and quotes found while parsing. */
    public val diagnostics: List<Diagnostic> get() { sections; checklists; learnings; workLog; quotes; return diagnosticList }

    private fun line(index: Int) = index + firstLine
    private fun text(fileLine: Int) = lines[fileLine - firstLine].trimEnd('\r')

    public val sections: List<Section> by lazy {
        val headings = mutableListOf<Pair<String, Int>>()
        var fence: String? = null
        lines.forEachIndexed { i, raw ->
            val l = raw.trimEnd('\r')
            val trimmed = l.trimStart()
            val marker = FENCE.find(trimmed)?.value
            when {
                fence != null -> if (marker != null && marker.first() == fence!!.first() && marker.length >= fence!!.length) fence = null
                marker != null -> fence = marker
                l.startsWith("## ") -> headings += l.removePrefix("## ").trim() to line(i)
            }
        }
        val lastFileLine = line(lines.size - 1)
        headings.mapIndexed { i, (name, at) ->
            val end = headings.getOrNull(i + 1)?.second?.minus(1) ?: lastFileLine
            Section(name, at, at + 1, end)
        }
    }

    public fun section(name: String): Section? = sections.firstOrNull { it.name == name }

    /** Whether a section has content: a non-blank line that isn't only an HTML comment. */
    public fun hasContent(section: Section): Boolean =
        content(section).any { (_, l) -> l.isNotBlank() && !HTML_COMMENT.matches(l.trim()) }

    /** A section's content as written, without the blank lines around it and comment-only lines. */
    public fun text(section: Section): String =
        content(section).map { it.second }.filterNot { HTML_COMMENT.matches(it.trim()) }.joinToString("\n").trim('\n', ' ')

    /** Non-blank content lines of a section, as (file line, text). */
    private fun content(section: Section): List<Pair<Int, String>> =
        (section.firstLine..section.lastLine).map { it to text(it) }

    /** Checklist items of a section, in order. */
    public fun checklist(name: String): List<CheckItem> = checklists[name] ?: emptyList()

    private val checklists: Map<String, List<CheckItem>> by lazy {
        sections.associate { section ->
            val items = mutableListOf<CheckItem>()
            for ((at, l) in content(section)) {
                val m = CHECK_ITEM.matchEntire(l)
                when {
                    m != null -> {
                        val itemText = m.groupValues[2]
                        val child = CHILD_ITEM.find(itemText)?.groupValues?.get(1)
                        items += CheckItem(m.groupValues[1] != " ", itemText, at, at, child)
                    }
                    items.isNotEmpty() && isContinuation(l) && items.last().lastLine == at - 1 ->
                        items[items.lastIndex] = items.last().copy(lastLine = at)
                }
            }
            section.name to items
        }
    }

    public val learnings: List<Learning> by lazy {
        val section = section("Learnings") ?: return@lazy emptyList()
        listItems(section).map { (start, end) ->
            val first = text(start).removePrefix("- ")
            val arrowLine = (start..end).lastOrNull { text(it).trimStart().startsWith("→") }
            val resolution = arrowLine?.let { resolution(text(it).trimStart().removePrefix("→").trim(), it) } ?: Resolution.Pending
            Learning(first, start, end, resolution, arrowLine)
        }
    }

    private fun resolution(text: String, at: Int): Resolution = when {
        text.startsWith("promoted:") -> Resolution.Promoted(text.removePrefix("promoted:").trim())
        text.startsWith("new ticket:") -> {
            val id = CHILD_ITEM.find(text.removePrefix("new ticket:").trim())?.groupValues?.get(1)
            if (id == null) report(at, "learning-resolution", "new ticket must name the id in backticks: → new ticket: `id`")
            Resolution.NewTicket(id ?: "")
        }
        text == "ticket only" -> Resolution.TicketOnly
        else -> {
            report(at, "learning-resolution", "expected '→ promoted: …', '→ new ticket: `id`' or '→ ticket only'")
            Resolution.Pending
        }
    }

    public val workLog: List<WorkLogEntry> by lazy {
        val section = section("Work Log") ?: return@lazy emptyList()
        listItems(section).mapNotNull { (start, end) ->
            val m = WORK_LOG_ENTRY.matchEntire(text(start))
            if (m == null) {
                report(start, "work-log-entry", "expected '- **YYYY-MM-DD** · ref · text'")
                null
            } else {
                val rest = ((start + 1)..end).joinToString("") { "\n" + text(it).trim() }
                WorkLogEntry(m.groupValues[1], m.groupValues[2], m.groupValues[3] + rest, start, end)
            }
        }
    }

    /** User Requests quotes: `> ` lines followed by `— ref · channel · date`. */
    public val quotes: List<Quote> by lazy {
        val section = section("User Requests") ?: return@lazy emptyList()
        val result = mutableListOf<Quote>()
        var quoteStart: Int? = null
        val quoteText = StringBuilder()
        for ((at, l) in content(section)) {
            when {
                l.startsWith(">") -> {
                    if (quoteStart == null) quoteStart = at else quoteText.append('\n')
                    quoteText.append(l.removePrefix(">").removePrefix(" "))
                }
                l.startsWith("—") && quoteStart != null -> {
                    val parts = l.removePrefix("—").split("·").map { it.trim() }
                    if (parts.size == 3) {
                        result += Quote(quoteText.toString(), quoteStart, parts[0], parts[1], parts[2], at)
                    } else {
                        report(at, "quote-attribution", "expected '— <ref> · <channel> · <date>'")
                    }
                    quoteStart = null
                    quoteText.clear()
                }
                l.isBlank() && quoteStart != null -> Unit
                quoteStart != null -> {
                    report(quoteStart, "quote-attribution", "quote without an attribution line '— <ref> · <channel> · <date>'")
                    quoteStart = null
                    quoteText.clear()
                }
            }
        }
        if (quoteStart != null) report(quoteStart, "quote-attribution", "quote without an attribution line '— <ref> · <channel> · <date>'")
        result
    }

    /** Top-level `- ` list items of a section with their indented continuation lines. */
    private fun listItems(section: Section): List<Pair<Int, Int>> {
        val items = mutableListOf<Pair<Int, Int>>()
        for ((at, l) in content(section)) {
            when {
                l.startsWith("- ") -> items += at to at
                items.isNotEmpty() && isContinuation(l) && items.last().second == at - 1 ->
                    items[items.lastIndex] = items.last().first to at
            }
        }
        return items
    }

    private fun isContinuation(l: String) = l.isNotBlank() && (l.startsWith(" ") || l.startsWith("\t"))

    private fun report(at: Int, code: String, message: String) {
        val d = Diagnostic(file, at, null, code, message)
        if (d !in diagnosticList) diagnosticList += d
    }

    private companion object {
        val FENCE = Regex("^(`{3,}|~{3,})")
        val HTML_COMMENT = Regex("^<!--.*-->$")
        val CHECK_ITEM = Regex("^- \\[([ xX])] ?(.*)$")
        val CHILD_ITEM = Regex("^`([a-z][a-z0-9-]*)`")
        // The ref may contain a space (`step 2`), not a `·`.
        val WORK_LOG_ENTRY = Regex("^- \\*\\*([0-9]{4}-[0-9]{2}-[0-9]{2})\\*\\* · ([^·]+?) · (.*)$")
    }
}
