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
 *
 * A process can't change its shell's directory, so `--print-path` prints only the directory to work
 * in (the rest goes to stderr) for a shell function to `cd` to; see the README.
 */
class StartCommand : RepositoryCommand(name = "start") {
    override fun help(context: Context) =
        "Start a ticket: create branch <id> (from the parent's branch when its children merge into it, " +
            "else from mainBranch), set status: in-progress and log it in a commit on that branch, then add the " +
            "worktree when safanoria.yaml has one, else switch to the branch."

    private val id by argument(help = "Ticket id")
    private val noSwitch by option("--no-switch", help = "Without a worktree setting: create the branch but don't switch this checkout to it").flag()
    private val printPath by option(
        "--print-path",
        help = "Print only the directory to work in (the worktree, or this checkout once switched) on stdout, everything else on stderr: dir=\$(safanoria start <id> --print-path) && cd \"\$dir\"",
    ).flag()
    private val dryRun by option("--dry-run", help = "Show what would be done, change nothing").flag()
    private val date by option("--date", hidden = true, help = "Today's date (tests)")

    /** With `--print-path`, stdout carries only the path: every message goes to stderr. */
    private fun say(message: String) = echo(message, err = printPath)

    override fun run() {
        if (printPath && noSwitch) throw PrintMessage("--print-path and --no-switch don't go together: with --no-switch there's no directory to go to.", 2, true)
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
        say("${verb}create branch $id from ${ready.base}")
        say("${verb}set status: in-progress and log 'status · started', committed on $id")
        when {
            ready.worktree != null -> say("${verb}add worktree ${ready.worktree}")
            !noSwitch -> say("${verb}switch this checkout to $id")
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
            say("worktree ${ready.worktree}: open a session there (cd ${ready.worktree} && claude)")
            if (printPath) echo(ready.worktree.toString())
        } else if (!noSwitch) {
            // Not switched (uncommitted changes) still prints this checkout: the note on stderr says why.
            switchHere(repo.git)
            if (printPath) echo(repo.root.toString())
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
        say("committed ${commit.take(7)} on $id: $id: start")
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
        say("switched to $id")
    }
}
