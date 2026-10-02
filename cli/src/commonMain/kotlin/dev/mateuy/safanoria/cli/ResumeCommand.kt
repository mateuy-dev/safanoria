package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import dev.mateuy.safanoria.core.Resume
import dev.mateuy.safanoria.core.ResumePoint
import dev.mateuy.safanoria.core.text
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * `safanoria resume [<id or words>]`: where work on a ticket is and how to get there. Reads only;
 * the agent (or the user) switches there.
 */
class ResumeCommand : AcrossBranchesCommand(name = "resume") {
    override fun help(context: Context) =
        "Show where to continue work: each ticket's branch, worktree, next Plan item and last Work Log entry. " +
            "Give a ticket id or words from its title; a parent leads to its children in progress. " +
            "Without arguments, every ticket in progress."

    private val query by argument(help = "Ticket id, or words from the title of a ticket in progress or review").multiple()
    private val format by option("--format", help = "Output format").choice("text", "json").default("text")

    override fun run() {
        val resume = Resume(repository, graph)
        val q = query.joinToString(" ")
        val points = resume.candidates(q).map(resume::point)
        if (format == "json") {
            echo(buildJsonObject { putJsonArray("tickets") { points.forEach { add(json(it)) } } }.toString())
        } else if (points.isEmpty()) {
            echo(if (q.isEmpty()) "No ticket in progress." else "No ticket in progress or review matches '$q'.", err = true)
        } else {
            echo(points.joinToString("\n") { text(it) })
        }
        if (points.isEmpty()) throw ProgramResult(1)
    }

    private fun text(p: ResumePoint): String = buildString {
        val f = p.ticket.frontmatter
        append("${p.ticket.fileId}  ${f?.status?.text ?: "-"}  ${f?.title?.value ?: "-"}")
        f?.parent?.value?.let { append("  (parent $it)") }
        append('\n')
        fun row(label: String, value: String) = append("  ${label.padEnd(9)} $value\n")
        row("branch", p.branch ?: "none")
        row("worktree", p.worktree?.let { "$it${uncommitted(p)}" } ?: "none")
        // First lines only, "…" when there is more: enough to recognise the step.
        row("next", p.next?.let { "[ ] ${it.text}${if (it.lastLine > it.line) " …" else ""}" } ?: "nothing unchecked in Plan")
        p.lastLog?.let { row("last log", "${it.date} · ${it.ref} · ${it.text.lineSequence().first()}${if ('\n' in it.text) " …" else ""}") }
        row("go there", goThere(p))
    }

    private fun uncommitted(p: ResumePoint) = when (p.uncommitted) {
        null, 0 -> ""
        1 -> "  (1 uncommitted file)"
        else -> "  (${p.uncommitted} uncommitted files)"
    }

    /** What to run to get there; null when already there. */
    private fun command(p: ResumePoint): String? {
        val id = p.ticket.fileId
        return when {
            p.worktree == repository.root -> null
            p.worktree != null -> "cd ${p.worktree}"
            p.branch == null -> null
            else -> repository.config.worktree?.let { "git worktree add ${it.replace("{id}", id)} $id" } ?: "git switch $id"
        }
    }

    private fun goThere(p: ResumePoint): String = command(p) ?: when {
        p.worktree != null -> "you are there"
        else -> "not started: no branch ${p.ticket.fileId}"
    }

    private fun json(p: ResumePoint): JsonObject = buildJsonObject {
        val f = p.ticket.frontmatter
        fun str(v: String?): JsonElement = v?.let(::JsonPrimitive) ?: JsonNull
        put("id", p.ticket.fileId)
        put("title", str(f?.title?.value))
        put("status", str(f?.status?.text))
        put("parent", str(f?.parent?.value))
        put("branch", str(p.branch))
        put("worktree", str(p.worktree?.toString()))
        put("here", p.worktree == repository.root)
        put("uncommitted", p.uncommitted?.let(::JsonPrimitive) ?: JsonNull)
        put("next", str(p.next?.text))
        put("lastLog", p.lastLog?.let { buildJsonObject { put("date", it.date); put("ref", it.ref); put("text", it.text) } } ?: JsonNull)
        put("command", str(command(p)))
    }
}
