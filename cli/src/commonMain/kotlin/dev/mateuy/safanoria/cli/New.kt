package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.choice
import dev.mateuy.safanoria.core.BranchView
import dev.mateuy.safanoria.core.Branches
import dev.mateuy.safanoria.core.Diagnostic
import dev.mateuy.safanoria.core.GitException
import dev.mateuy.safanoria.core.NewTicket
import dev.mateuy.safanoria.core.NewTicketRequest
import dev.mateuy.safanoria.core.NewTicketResult
import dev.mateuy.safanoria.core.Priority
import dev.mateuy.safanoria.core.Repository
import dev.mateuy.safanoria.core.Size
import dev.mateuy.safanoria.core.Status
import dev.mateuy.safanoria.core.TicketType
import dev.mateuy.safanoria.core.Validator
import dev.mateuy.safanoria.core.text
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import okio.Path
import okio.Path.Companion.toPath
import kotlin.time.Clock

/** `safanoria-cli new "<title>"`: SPEC §11 Create, the mechanical part. */
class New : RepositoryCommand(name = "new") {
    override fun help(context: Context) =
        "Create a backlog ticket from a title: suggests an id (or use --id), checks it isn't taken, " +
            "fills the template and, with --parent, adds it to the parent's Plan. Use --dry-run to see the id first."

    private val titleArgument by argument(name = "title", help = "One-line title (on a terminal: asks for it, then for the type, the id and optionally the rest)").optional()
    private val idOption by option("--id", help = "Ticket id (default: suggested from the title)")
    private val parentOption by option("--parent", help = "Parent ticket id: sets parent and adds a Plan item to it")
    private val typeOption by option("--type", help = "(default: feature)").choice(*TicketType.entries.map { it.text }.toTypedArray())
    private val priorityOption by option("--priority", help = "(default: medium)").choice(*Priority.entries.map { it.text }.toTypedArray())
    private val sizeOption by option("--size", help = "(default: S)").choice(*Size.entries.map { it.text }.toTypedArray())
    private val areaOption by option("--area", help = "Components, comma-separated (required with several components)").split(",")
    private val objectiveOption by option("--objective", help = "Text for the Objective section")
    private val onOption by option("--on", help = "Create it on this branch and commit it there, without touching this checkout, e.g. --on main for work found on another branch (SPEC §14.2)")
    private val dryRun by option("--dry-run", help = "Show what would be created, write nothing").flag()
    private val date by option("--date", hidden = true, help = "Today's date (tests)")

    private fun create() {
        val repo = repository
        val today = date ?: Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        val branches = Branches.read(repo, remote = true)
        val prompts = prompts
        // Asking happens when the title is missing: with a title, flags and defaults decide, as in a script.
        val asking = titleArgument == null
        if (asking && prompts == null) throw UsageError("missing argument <title>").apply { context = currentContext }
        if (asking && prompts != null) ask(prompts, repo, branches)
        val components = repo.config.components.keys.toList()
        if (area == null && components.size > 1 && prompts != null) area = askArea(prompts, components)

        val view = on?.let { BranchView.open(repo, it) ?: throw PrintMessage("No local branch '$it'.", 2, true) }
        val target = view?.repository ?: repo
        val taken = branches?.ids.orEmpty()
        fun prepare() = when (val result = NewTicket.prepare(target, request(), today, taken)) {
            is NewTicketResult.Refused -> throw PrintMessage("Not created: ${result.reason}${on?.let { " (on $it)" } ?: ""}", 1, true)
            is NewTicketResult.Ready -> result
        }
        var ready = prepare()
        if (asking && prompts != null && id == null) {
            val chosen = prompts.text("Id, also the branch name", ready.id).trim()
            if (chosen != ready.id) {
                id = chosen
                ready = prepare()
            }
        }

        val verb = if (dryRun) "would " else ""
        // A branch that isn't checked out has no files to point at: show the path in the branch.
        fun shown(path: Path) = if (view != null && view.worktree == null) "${path.relativeTo(target.root).segments.joinToString("/")} on ${view.branch}" else displayPath(path)
        for (f in ready.files) {
            echo(if (f.isNew) "${verb}create ${shown(f.path)}" else "${verb}update ${shown(f.path)} (Plan: - [ ] `${ready.id}`: ${title.trim()})")
        }
        if (ready.idSuggested && !asking) echo("id ${ready.id}: suggested from the title; use --id to choose another")
        branchWarning(repo, ready.id)
        if (view == null && parent == null && !asking) mainBranchHint(repo)
        if (dryRun) return

        if (view != null) {
            // Checked before committing: a commit on another branch is harder to take back than a file.
            val problems = Validator(view.withFiles(ready.files)).validate(ready.files.map { it.path })
            report(problems)
            val commit = try { view.commit(ready.files, "${ready.id}: create") } catch (e: GitException) {
                throw PrintMessage("Not created: ${e.message}", 1, true)
            }
            echo("committed ${commit.take(7)} on ${view.branch}: ${ready.id}: create")
            return
        }
        ready.files.forEach { f -> repo.fileSystem.write(f.path) { writeUtf8(f.text) } }
        report(Validator(Repository(repo.root, repo.fileSystem)).validate(ready.files.map { it.path }))
    }

    // Values from the flags, then from the answers.
    private var title: String = ""
    private var id: String? = null
    private var parent: String? = null
    private var type = "feature"
    private var priority = "medium"
    private var size = "S"
    private var area: List<String>? = null
    private var objective: String? = null
    private var on: String? = null

    override fun run() {
        title = titleArgument.orEmpty()
        id = idOption
        parent = parentOption
        typeOption?.let { type = it }
        priorityOption?.let { priority = it }
        sizeOption?.let { size = it }
        area = areaOption
        objective = objectiveOption
        on = onOption
        create()
    }

    private fun request() = NewTicketRequest(
        title = title,
        id = id,
        parent = parent,
        type = TicketType.entries.first { it.text == type },
        priority = Priority.entries.first { it.text == priority },
        size = Size.entries.first { it.text == size },
        area = area ?: emptyList(),
        objective = objective,
    )

    /** Title, type, then the rest only if wanted, then the branch; values given as flags aren't asked. */
    private fun ask(prompts: Prompts, repo: Repository, branches: Branches?) {
        while (title.isBlank()) title = prompts.text("Title").trim()
        if (typeOption == null) type = prompts.choose("Type", TicketType.entries.map { Choice(it.text) }, type)
        val open = branches?.tickets.orEmpty().filter { it.frontmatter?.status !in setOf(Status.DONE, Status.WONTFIX) }
        if (prompts.confirm("Set priority, size${if (open.isNotEmpty()) ", parent" else ""} or objective?", default = false)) {
            if (priorityOption == null) priority = prompts.choose("Priority", Priority.entries.map { Choice(it.text) }, priority)
            if (sizeOption == null) size = prompts.choose("Size", Size.entries.map { Choice(it.text) }, size)
            if (parentOption == null && open.isNotEmpty()) {
                val none = Choice("", "(none)", "a top-level ticket")
                parent = prompts.choose("Parent", listOf(none) + open.map(::ticketChoice), "").ifEmpty { null }
            }
            if (objectiveOption == null) objective = prompts.text("Objective (what is wanted and why; empty to write it later)").trim().ifEmpty { null }
        }
        // SPEC §14.2: the question mainBranchHint() would otherwise only warn about.
        if (onOption == null && parent == null) {
            val current = try { repo.git.currentBranch() } catch (e: GitException) { return }
            val main = repo.config.mainBranch
            if (current == main || current == "HEAD") return
            val where = prompts.choose(
                "This is branch '$current'. Where does the ticket go?",
                listOf(
                    Choice(main, description = "commit it on $main, without touching this checkout (a top-level ticket)"),
                    Choice(current, description = "write it here: part of this branch's work"),
                ),
                main,
            )
            if (where == main) on = main
        }
    }

    private fun askArea(prompts: Prompts, components: List<String>): List<String> {
        while (true) {
            val chosen = prompts.chooseMany("Area: which components", components.map { Choice(it) })
            if (chosen.isNotEmpty()) return chosen
        }
    }

    private fun report(problems: List<Diagnostic>) {
        if (problems.isEmpty()) return
        problems.forEach { echo(it.copy(file = it.file?.let { p -> displayPath(p).toPath() }).toString(), err = true) }
        throw ProgramResult(1)
    }

    /** SPEC §14.2: a top-level ticket belongs on mainBranch; say how when the user is elsewhere. */
    private fun mainBranchHint(repo: Repository) {
        val current = try { repo.git.currentBranch() } catch (e: GitException) { return }
        val main = repo.config.mainBranch
        if (current != main && current != "HEAD") {
            echo("note: this is branch '$current'; a top-level ticket belongs on $main (SPEC §14.2): use --on $main unless it's part of this branch's work", err = true)
        }
    }

    /** §3: an id SHOULD NOT clash with an existing branch. A warning, since it may be this ticket's. */
    private fun branchWarning(repo: Repository, id: String) {
        val exists = try { repo.git.branchExists(id) } catch (e: GitException) { return } // not a git repository
        if (exists) echo("warning: a branch named '$id' already exists (SPEC §3); choose another id with --id unless it's this ticket's", err = true)
    }
}
