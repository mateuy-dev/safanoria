package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.choice
import dev.mateuy.safanoria.core.Status
import dev.mateuy.safanoria.core.Ticket
import dev.mateuy.safanoria.core.TicketFilter
import dev.mateuy.safanoria.core.TicketGraph
import dev.mateuy.safanoria.core.TicketType
import dev.mateuy.safanoria.core.text
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** `safanoria list`: one line per ticket, in board order; `--format json` for agents. */
class ListTickets : RepositoryCommand(name = "list") {
    override fun help(context: Context) =
        "List tickets, one per line, in-progress first. Filters combine; comma-separated values are alternatives."

    private val status by option("--status", help = "Statuses, e.g. in-progress,review").choice(Status.entries.associateBy { it.text }).split(",")
    private val type by option("--type", help = "Types, e.g. bug,feature").choice(TicketType.entries.associateBy { it.text }).split(",")
    private val area by option("--area", help = "Components").split(",")
    private val parent by option("--parent", help = "Only children of this ticket")
    private val blocked by option("--blocked", help = "Only tickets blocked by a ticket that is not done").flag()
    private val format by option("--format", help = "Output format").choice("text", "json").default("text")

    override fun run() {
        val filter = TicketFilter(
            statuses = status.orEmpty().toSet(),
            types = type.orEmpty().toSet(),
            areas = area.orEmpty().toSet(),
            parent = parent,
            blocked = blocked,
        )
        val graph = repository.graph
        val shown = graph.tickets.filter { filter.matches(graph, it) }
        if (format == "json") echo(json(graph, shown).toString()) else text(graph, shown).forEach { echo(it) }
    }

    /** Aligned columns: id, status, priority, type, size, title, then markers. */
    private fun text(graph: TicketGraph, tickets: List<Ticket>): List<String> {
        val rows = tickets.map { t ->
            val f = t.frontmatter
            listOf(t.fileId, f?.status?.text, f?.priority?.text, f?.type?.text, f?.size?.text).map { it ?: "-" } to
                (listOf(f?.title?.value ?: "-") + markers(graph, t)).joinToString("  ")
        }
        val widths = (0 until 5).map { col -> rows.maxOfOrNull { it.first[col].length } ?: 0 }
        return rows.map { (cols, rest) -> cols.mapIndexed { i, c -> c.padEnd(widths[i]) }.joinToString("  ") + "  " + rest }
    }

    private fun markers(graph: TicketGraph, t: Ticket): List<String> = listOfNotNull(
        graph.progress(t)?.takeIf { graph.children(t).isNotEmpty() }?.let { "[${it.done}/${it.total}]" },
        t.frontmatter?.parent?.value?.let { "parent $it" },
        graph.openBlockers(t).takeIf { it.isNotEmpty() }?.let { "blocked by ${it.joinToString(", ")}" },
    )

    private fun json(graph: TicketGraph, tickets: List<Ticket>): JsonObject = buildJsonObject {
        putJsonArray("tickets") { tickets.forEach { add(ticketJson(graph, it)) } }
    }

    private fun ticketJson(graph: TicketGraph, t: Ticket): JsonObject = buildJsonObject {
        val f = t.frontmatter
        fun str(v: String?): JsonElement = v?.let(::JsonPrimitive) ?: JsonNull
        fun ids(v: List<String>) = JsonArray(v.map(::JsonPrimitive))
        put("id", t.fileId)
        put("file", t.path.relativeTo(repository.root).segments.joinToString("/")) // `/` on every OS, like the board's links
        put("title", str(f?.title?.value))
        put("type", str(f?.type?.text))
        put("status", str(f?.status?.text))
        put("priority", str(f?.priority?.text))
        put("size", str(f?.size?.text))
        put("created", str(f?.created?.value))
        put("updated", str(f?.updated?.value))
        put("assignee", str(f?.assignee?.value))
        put("area", ids(f?.area.orEmpty().map { it.value }))
        put("parent", str(f?.parent?.value))
        put("childrenMergeInto", str(f?.childrenMergeInto))
        put("blockedBy", ids(f?.blockedBy.orEmpty().map { it.value }))
        put("related", ids(f?.related.orEmpty().map { it.value }))
        putJsonObject("refs") { f?.refs.orEmpty().forEach { (k, v) -> put(k, ids(v)) } }
        putJsonArray("requests") {
            f?.requests.orEmpty().forEach { r ->
                add(buildJsonObject { put("user", str(r.user)); put("who", str(r.who)); put("channel", str(r.channel)); put("date", str(r.date)) })
            }
        }
        put("resolvedIn", f?.resolvedIn?.let { m -> JsonObject(m.mapValues { str(it.value.value) }) } ?: JsonNull)
        put("children", ids(graph.children(t).map { it.fileId }))
        put("blocks", ids(graph.blocks(t).map { it.fileId }))
        put("openBlockers", ids(graph.openBlockers(t)))
        put("progress", graph.progress(t)?.let { buildJsonObject { put("done", it.done); put("total", it.total) } } ?: JsonNull)
    }
}
