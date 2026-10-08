package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import dev.mateuy.safanoria.core.Branches
import dev.mateuy.safanoria.core.GitException
import dev.mateuy.safanoria.core.Start
import dev.mateuy.safanoria.core.StartException
import dev.mateuy.safanoria.core.StartResult
import dev.mateuy.safanoria.core.Status
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import okio.Path.Companion.toPath
import kotlin.time.Clock

/**
 * `safanoria-cli start <id>`: SPEC §11.2 Start. Creates branch `<id>`, commits the ticket started on
 * it (without checking it out, so nothing in this checkout changes until the branch is ready),
 * then adds its worktree.
 *
 * A process can't change its shell's directory, so `--print-path` prints only the worktree's
 * path (the rest goes to stderr) for a shell function to `cd` to; see `safanoria-cli --help` and the README.
 */
class StartCommand : RepositoryCommand(name = "start") {
    override fun help(context: Context) =
        "Start a ticket: create branch <id> (from the parent's branch when its children merge into it, " +
            "else from mainBranch), set status: in-progress and log it in a commit on that branch, then add its " +
            "worktree where safanoria.yaml says (worktree)."

    private val idArgument by argument(name = "id", help = "Ticket id (on a terminal: asks, from the backlog and ready tickets)").optional()
    private val printPath by option(
        "--print-path",
        help = "Print only the worktree's path on stdout, everything else on stderr: dir=\$(safanoria-cli start <id> --print-path) && cd \"\$dir\"",
    ).flag()
    private val dryRun by option("--dry-run", help = "Show what would be done, change nothing").flag()
    private val date by option("--date", hidden = true, help = "Today's date (tests)")

    private lateinit var id: String

    /** With `--print-path`, stdout carries only the path: every message goes to stderr. */
    private fun say(message: String) = echo(message, err = printPath)

    override fun run() {
        val repo = repository
        val today = date ?: Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        val branches = Branches.read(repo) ?: throw PrintMessage(
            "Not started: needs a git repository with the branch '${repo.config.mainBranch}' (mainBranch).", 1, true,
        )
        id = idArgument ?: askTicket(branches, "Start which ticket?", setOf(Status.BACKLOG, Status.READY), "nothing to start: no backlog or ready ticket")
        val ready = when (val r = Start.prepare(repo, branches, id)) {
            is StartResult.Refused -> throw PrintMessage("Not started: ${r.reason}", 1, true)
            is StartResult.Ready -> r
        }
        val verb = if (dryRun) "would " else ""
        say("${verb}create branch $id from ${ready.base}")
        say("${verb}set status: in-progress and log 'status · started', committed on $id")
        say("${verb}add worktree ${ready.worktree}")
        if (dryRun) return

        val commit = try { Start.begin(repo, ready, today) } catch (e: StartException) {
            if (e.problems.isEmpty()) throw PrintMessage("Not started: ${e.message}", 1, true)
            e.problems.forEach { echo(it.copy(file = it.file?.let { p -> displayPath(p).toPath() }).toString(), err = true) }
            throw ProgramResult(1)
        }
        say("committed ${commit.take(7)} on $id: $id: start")

        try { Start.addWorktree(repo, ready) } catch (e: GitException) {
            throw PrintMessage("Started, but the worktree wasn't added: ${e.message}\nAdd it with: git worktree add ${ready.worktree} $id", 1, true)
        }
        say("worktree ${ready.worktree}: open a session there (cd ${ready.worktree} && claude)")
        if (printPath) echo(ready.worktree.toString())
    }
}

/**
 * The shell function `safanoria-cli --help` and the README give: starts a ticket and `cd`s to where
 * `--print-path` says. One help line each (the help formatter drops indentation and would re-wrap a one-liner).
 */
internal val SHELL_FUNCTION = listOf(
    "safanoria-start() {",
    "local d",
    "d=\$(safanoria-cli start \"\$@\" --print-path) && [ -n \"\$d\" ] && cd \"\$d\"",
    "}",
)
