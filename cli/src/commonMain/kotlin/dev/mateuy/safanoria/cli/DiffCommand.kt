package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import dev.mateuy.safanoria.core.Branches
import dev.mateuy.safanoria.core.DiffResult
import dev.mateuy.safanoria.core.Review
import dev.mateuy.safanoria.core.Status
import dev.mateuy.safanoria.core.TicketDiff
import dev.mateuy.safanoria.tui.runHere

/**
 * `safanoria-cli diff <id> [--stat | --tool] [--uncommitted]`: a quick look at a ticket's changes
 * (see [TicketDiff]), where review mode would be too much.
 */
class DiffCommand : RepositoryCommand(name = "diff") {
    override fun help(context: Context) =
        "A ticket's changes against its target (main, or the parent's branch), from their merge-base so nothing new on the " +
            "target shows: git diff, or --tool for git's diff tool (git config diff.tool). Changes nothing; for the IDE on " +
            "the real code, use review."

    private val idArgument by argument(name = "id", help = "Ticket id (default: this worktree's ticket; elsewhere, on a terminal, asks)").optional()
    private val stat by option("--stat", help = "Only the changed files and their line counts").flag()
    private val tool by option("--tool", help = "Open it in git's diff tool (difftool --dir-diff: the changed files, in two temporary directories)").flag()
    private val uncommitted by option("--uncommitted", help = "Up to the files in the ticket's worktree, uncommitted changes included (untracked files aside)").flag()

    override fun run() {
        if (stat && tool) throw UsageError("--stat and --tool don't go together")
        val repo = repository
        val branches = Branches.read(repo) ?: throw PrintMessage(
            "No diff: needs a git repository with the branch '${repo.config.mainBranch}' (mainBranch).", 1, true,
        )
        val id = idArgument ?: currentTicket(branches) ?: askTicket(
            branches, "Diff of which ticket?", setOf(Status.IN_PROGRESS, Status.REVIEW), "nothing to show: no ticket in progress or in review",
        )
        val ready = when (val r = TicketDiff.prepare(repo, branches, id, uncommitted)) {
            is DiffResult.Refused -> throw PrintMessage("No diff: ${r.reason}", 1, true)
            is DiffResult.Ready -> r
        }
        val command = TicketDiff.command(repo.root, ready, tool, stat, uncommitted)
        ready.warnings.forEach { echo("warning: $it", err = true) }
        if (tool && !TicketDiff.hasTool(repo.git)) {
            echo("no diff tool set, so git picks one: set yours with git config --global diff.tool <tool> (README: Diff)", err = true)
        }
        val exit = runHere(repo.root.toString(), command) ?: throw PrintMessage("Couldn't run git", 1, true)
        if (exit != 0) throw ProgramResult(exit)
    }

    /** This worktree's ticket: its branch's, or the one it is in review mode for. */
    private fun currentTicket(branches: Branches): String? = runCatching {
        Review.state(repository.root)?.id ?: repository.git.currentBranch().takeIf { branches.graph.ticket(it) != null }
    }.getOrNull()
}
