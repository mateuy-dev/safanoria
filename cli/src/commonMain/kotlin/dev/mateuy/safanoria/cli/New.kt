package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.choice
import dev.mateuy.safanoria.core.GitException
import dev.mateuy.safanoria.core.NewTicket
import dev.mateuy.safanoria.core.NewTicketRequest
import dev.mateuy.safanoria.core.NewTicketResult
import dev.mateuy.safanoria.core.Priority
import dev.mateuy.safanoria.core.Repository
import dev.mateuy.safanoria.core.Size
import dev.mateuy.safanoria.core.TicketType
import dev.mateuy.safanoria.core.Validator
import dev.mateuy.safanoria.core.text
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import okio.Path.Companion.toPath
import kotlin.time.Clock

/** `safanoria new "<title>"`: SPEC §11 Create, the mechanical part. */
class New : RepositoryCommand(name = "new") {
    override fun help(context: Context) =
        "Create a backlog ticket from a title: suggests an id (or use --id), checks it isn't taken, " +
            "fills the template and, with --parent, adds it to the parent's Plan. Use --dry-run to see the id first."

    private val title by argument(help = "One-line title")
    private val id by option("--id", help = "Ticket id (default: suggested from the title)")
    private val parent by option("--parent", help = "Parent ticket id: sets parent and adds a Plan item to it")
    private val type by option("--type").choice(*TicketType.entries.map { it.text }.toTypedArray()).default("feature")
    private val priority by option("--priority").choice(*Priority.entries.map { it.text }.toTypedArray()).default("medium")
    private val size by option("--size").choice(*Size.entries.map { it.text }.toTypedArray()).default("S")
    private val area by option("--area", help = "Components, comma-separated (required with several components)").split(",")
    private val objective by option("--objective", help = "Text for the Objective section")
    private val dryRun by option("--dry-run", help = "Show what would be created, write nothing").flag()
    private val date by option("--date", hidden = true, help = "Today's date (tests)")

    override fun run() {
        val repo = repository
        val today = date ?: Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        val request = NewTicketRequest(
            title = title,
            id = id,
            parent = parent,
            type = TicketType.entries.first { it.text == type },
            priority = Priority.entries.first { it.text == priority },
            size = Size.entries.first { it.text == size },
            area = area ?: emptyList(),
            objective = objective,
        )
        val ready = when (val result = NewTicket.prepare(repo, request, today)) {
            is NewTicketResult.Refused -> throw PrintMessage("Not created: ${result.reason}", 1, true)
            is NewTicketResult.Ready -> result
        }

        val verb = if (dryRun) "would " else ""
        for (f in ready.files) {
            echo(if (f.isNew) "${verb}create ${displayPath(f.path)}" else "${verb}update ${displayPath(f.path)} (Plan: - [ ] `${ready.id}`: ${title.trim()})")
        }
        if (ready.idSuggested) echo("id ${ready.id}: suggested from the title; use --id to choose another")
        branchWarning(repo, ready.id)
        if (dryRun) return

        ready.files.forEach { f -> repo.fileSystem.write(f.path) { writeUtf8(f.text) } }
        val problems = Validator(Repository(repo.root, repo.fileSystem)).validate(ready.files.map { it.path })
        if (problems.isNotEmpty()) {
            problems.forEach { echo(it.copy(file = it.file?.let { p -> displayPath(p).toPath() }).toString(), err = true) }
            throw ProgramResult(1)
        }
    }

    /** §3: an id SHOULD NOT clash with an existing branch. A warning, since it may be this ticket's. */
    private fun branchWarning(repo: Repository, id: String) {
        val exists = try { repo.git.branchExists(id) } catch (e: GitException) { return } // not a git repository
        if (exists) echo("warning: a branch named '$id' already exists (SPEC §3); choose another id with --id unless it's this ticket's", err = true)
    }
}
