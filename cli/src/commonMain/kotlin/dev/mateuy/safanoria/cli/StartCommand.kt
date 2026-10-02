package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import dev.mateuy.safanoria.core.BranchView
import dev.mateuy.safanoria.core.Branches
import dev.mateuy.safanoria.core.Git
import dev.mateuy.safanoria.core.GitException
import dev.mateuy.safanoria.core.PlannedFile
import dev.mateuy.safanoria.core.Repository
import dev.mateuy.safanoria.core.Start
import dev.mateuy.safanoria.core.StartResult
import dev.mateuy.safanoria.core.TicketEditException
import dev.mateuy.safanoria.core.Validator
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import okio.Path.Companion.toPath
import kotlin.time.Clock

/**
 * `safanoria start <id>`: SPEC §11.2 Start. Creates branch `<id>`, commits the ticket started on
 * it (without checking it out, so nothing in this checkout changes until the branch is ready),
 * then adds the worktree when configured, else switches this checkout to the branch.
 */
class StartCommand : RepositoryCommand(name = "start") {
    override fun help(context: Context) =
        "Start a ticket: create branch <id> (from the parent's branch when its children merge into it, " +
            "else from mainBranch), set status: in-progress and log it in a commit on that branch, then add the " +
            "worktree when safanoria.yaml has one, else switch to the branch."

    private val id by argument(help = "Ticket id")
    private val noSwitch by option("--no-switch", help = "Without a worktree setting: create the branch but don't switch this checkout to it").flag()
    private val dryRun by option("--dry-run", help = "Show what would be done, change nothing").flag()
    private val date by option("--date", hidden = true, help = "Today's date (tests)")

    override fun run() {
        val repo = repository
        val today = date ?: Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        val branches = Branches.read(repo) ?: throw PrintMessage(
            "Not started: needs a git repository with the branch '${repo.config.mainBranch}' (mainBranch).", 1, true,
        )
        val ready = when (val r = Start.prepare(repo, branches, id)) {
            is StartResult.Refused -> throw PrintMessage("Not started: ${r.reason}", 1, true)
            is StartResult.Ready -> r
        }
        val verb = if (dryRun) "would " else ""
        echo("${verb}create branch $id from ${ready.base}")
        echo("${verb}set status: in-progress and log 'status · started', committed on $id")
        when {
            ready.worktree != null -> echo("${verb}add worktree ${ready.worktree}")
            !noSwitch -> echo("${verb}switch this checkout to $id")
        }
        if (dryRun) return

        val git = repo.git
        git.run("branch", id, ready.base)
        // Until the commit is made, a failure takes the branch back: a half-started ticket is worse than none.
        try {
            commitStart(repo, today)
        } catch (e: Exception) {
            runCatching { git.run("branch", "-D", id) }
            throw e
        }

        if (ready.worktree != null) {
            try { git.run("worktree", "add", ready.worktree.toString(), id) } catch (e: GitException) {
                throw PrintMessage("Started, but the worktree wasn't added: ${e.message}\nAdd it with: git worktree add ${ready.worktree} $id", 1, true)
            }
            echo("worktree ${ready.worktree}: open a session there (cd ${ready.worktree} && claude)")
        } else if (!noSwitch) {
            switchHere(repo.git)
        }
    }

    private fun commitStart(repo: Repository, today: String) {
        // The new branch isn't checked out anywhere, so the view reads its commit: the ticket as committed on the base.
        val view = BranchView.open(repo, id) ?: throw PrintMessage("Not started: can't read the new branch '$id'.", 1, true)
        val ticket = view.repository.ticket(id)
            ?: throw PrintMessage("Not started: no ticket '$id' committed on the branch it starts from.", 1, true)
        val text = try { Start.edit(ticket.text, today) } catch (e: TicketEditException) {
            throw PrintMessage("Not started: ${e.message}", 1, true)
        }
        val file = PlannedFile(ticket.path, text, isNew = false)
        val problems = Validator(view.withFiles(listOf(file))).validate(listOf(file.path))
        if (problems.isNotEmpty()) {
            problems.forEach { echo(it.copy(file = it.file?.let { p -> displayPath(p).toPath() }).toString(), err = true) }
            throw ProgramResult(1)
        }
        val commit = view.commit(listOf(file), "$id: start")
        echo("committed ${commit.take(7)} on $id: $id: start")
    }

    /** Switches this checkout to the new branch, unless it has uncommitted changes that would come along. */
    private fun switchHere(git: Git) {
        val dirty = git.run("status", "--porcelain", "--untracked-files=no").lines().any { it.isNotBlank() }
        if (dirty) {
            echo("note: this checkout has uncommitted changes, so it stays where it is; switch with: git switch $id", err = true)
            return
        }
        try { git.run("switch", "-q", id) } catch (e: GitException) {
            echo("note: couldn't switch to $id (${e.message}); switch with: git switch $id", err = true)
            return
        }
        echo("switched to $id")
    }
}
