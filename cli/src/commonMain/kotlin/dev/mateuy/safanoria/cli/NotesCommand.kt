package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import dev.mateuy.safanoria.core.NotesRequest
import dev.mateuy.safanoria.core.NotesResult
import dev.mateuy.safanoria.core.ReleaseNotes
import dev.mateuy.safanoria.core.Ticket
import dev.mateuy.safanoria.core.text

/**
 * `safanoria notes <component> <version> [<to>]`: what a version shipped, by `resolvedIn` (SPEC §9).
 * Prints the tickets, not the release notes: those are prose for end users, written from this.
 */
class NotesCommand : RepositoryCommand(name = "notes") {
    override fun help(context: Context) =
        "Print the tickets a version of a component shipped (resolvedIn), with their Objective: the " +
            "material to write its release notes from. With two versions, what changed after the first, up to and including the second."

    private val component by argument(help = "Component from safanoria.yaml")
    private val version by argument(help = "MAJOR.MINOR.PATCH: the version to describe; with <to>, the one the user already has (excluded)")
    private val to by argument(help = "MAJOR.MINOR.PATCH: the version the user updates to (included)").optional()

    override fun run() {
        val ready = when (val r = ReleaseNotes.collect(repository, NotesRequest(component, version, to))) {
            is NotesResult.Refused -> throw PrintMessage(r.reason, 1, true)
            is NotesResult.Ready -> r
        }
        val range = ready.after?.let { "after $it, up to ${ready.version}" } ?: "${ready.version}"
        if (ready.releases.isEmpty()) {
            val known = ready.stamped.takeIf { it.isNotEmpty() }?.joinToString(prefix = "stamped versions: ") ?: "no version is stamped yet"
            echo("no ticket has resolvedIn.${ready.component} $range ($known)", err = true)
            return
        }
        val lines = mutableListOf<String>()
        ready.releases.forEach { release ->
            lines += "# ${ready.component} ${release.version}"
            release.tickets.forEach { lines += ""; lines += ticket(it) }
            lines += ""
        }
        echo(lines.dropLast(1).joinToString("\n"))
    }

    private fun ticket(t: Ticket): List<String> {
        val f = t.frontmatter
        val objective = t.body.section("Objective")?.let(t.body::text).orEmpty()
        return listOfNotNull(
            "## `${t.fileId}` · ${f?.type?.text ?: "-"} · ${f?.title?.value ?: "-"}",
            // A child is a part of its parent's feature: the notes tell it once.
            f?.parent?.value?.let { "parent: `$it`" },
            f?.requests?.size?.takeIf { it > 0 }?.let { "user requests: $it" },
        ) + listOf("") + objective.ifEmpty { "(no Objective)" }
    }
}
