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
import dev.mateuy.safanoria.core.FinishException
import dev.mateuy.safanoria.core.Land
import dev.mateuy.safanoria.core.LandException
import dev.mateuy.safanoria.core.LandResult
import dev.mateuy.safanoria.core.Reopen
import dev.mateuy.safanoria.core.ReopenResult
import dev.mateuy.safanoria.core.Status
import dev.mateuy.safanoria.core.SystemFileSystem
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import okio.Path.Companion.toPath
import kotlin.time.Clock

/**
 * `safanoria-cli merge <id>`: SPEC §11.5, landing a ticket in review. One merge commit on the
 * target with the ticket `done` in it, then the worktree and the branch go. From any checkout.
 */
class MergeCommand : RepositoryCommand(name = "merge") {
    override fun help(context: Context) =
        "Land a ticket in review: merge its branch into its target (the main branch, or the parent's) with a merge " +
            "commit that also sets status: done, logs it and checks the parent's Plan item; then remove its worktree " +
            "and delete its branch. Pushes nothing. Refuses, changing nothing, on uncommitted changes in the " +
            "worktree, a conflict, or tickets that wouldn't validate."

    private val idArgument by argument(name = "id", help = "Ticket id (on a terminal: asks, starting on this branch's ticket)").optional()
    private val dryRun by option("--dry-run", help = "Check that it can be merged, change nothing").flag()
    private val date by option("--date", hidden = true, help = "Today's date (tests)")

    override fun run() {
        val repo = repository
        val today = date ?: Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        val branches = Branches.read(repo) ?: throw PrintMessage(
            "Not merged: needs a git repository with the branch '${repo.config.mainBranch}' (mainBranch).", 1, true,
        )
        val id = idArgument ?: askTicket(
            branches, "Merge which ticket?", setOf(Status.REVIEW), "nothing to merge: no ticket in review",
            default = runCatching { repo.git.currentBranch() }.getOrNull(),
        )
        val ready = when (val r = Land.prepare(repo, branches, id)) {
            is LandResult.Refused -> throw PrintMessage("Not merged: ${r.reason}", 1, true)
            is LandResult.Ready -> r
        }
        val parent = ready.parent
        if (dryRun) {
            echo("would merge '$id' into ${ready.target} with status: done, then remove its worktree and delete the branch")
            if (parent != null) echo("would check '$id' in the Plan of ${parent.fileId}, on ${ready.parentBranch}")
            return
        }
        val landed = try {
            Land.perform(repo, ready, today)
        } catch (e: LandException) {
            if (e.problems.isEmpty()) throw PrintMessage("Not merged: ${e.message}", 1, true)
            echo("Not merged: ${e.message}", err = true)
            e.problems.forEach { echo(it.copy(file = it.file?.let { p -> displayPath(p).toPath() }).toString(), err = true) }
            throw ProgramResult(1)
        }
        echo("merged '$id' into ${landed.target} with status: done: ${landed.commit.take(7)} ${landed.message}")
        landed.cleanUp.forEach { echo(it) }
        if (!SystemFileSystem.exists(repo.root)) echo("this directory was its worktree and is gone: cd somewhere else")
        echo("nothing was pushed")
    }
}

/** `safanoria-cli reopen <id> --reason "…"`: SPEC §6.1, back from review to in-progress with the reason logged. */
class ReopenCommand : RepositoryCommand(name = "reopen") {
    override fun help(context: Context) =
        "Send a ticket in review back to in-progress, logging why. Same branch and worktree; commits only the ticket."

    private val idArgument by argument(name = "id", help = "Ticket id (on a terminal: asks, starting on this branch's ticket)").optional()
    private val reasonOption by option("--reason", help = "What the review found, for the Work Log (on a terminal: asks)")
    private val date by option("--date", hidden = true, help = "Today's date (tests)")

    override fun run() {
        val repo = repository
        val today = date ?: Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        val branches = Branches.read(repo) ?: throw PrintMessage(
            "Not reopened: needs a git repository with the branch '${repo.config.mainBranch}' (mainBranch).", 1, true,
        )
        val id = idArgument ?: askTicket(
            branches, "Send which ticket back to in-progress?", setOf(Status.REVIEW), "nothing to reopen: no ticket in review",
            default = runCatching { repo.git.currentBranch() }.getOrNull(),
        )
        val reason = reasonOption
            ?: prompts?.text("What did the review find?")
            ?: throw UsageError("missing option --reason").apply { context = currentContext }
        val ready = when (val r = Reopen.prepare(branches, id, reason)) {
            is ReopenResult.Refused -> throw PrintMessage("Not reopened: ${r.reason}", 1, true)
            is ReopenResult.Ready -> r
        }
        try {
            val c = Reopen.perform(repo, ready, today)
            echo("'$id' is in-progress again, committed ${c.commit.take(7)} on ${c.branch}: ${c.message}")
        } catch (e: FinishException) {
            if (e.problems.isEmpty()) throw PrintMessage("Not reopened: ${e.message}", 1, true)
            e.problems.forEach { echo(it.copy(file = it.file?.let { p -> displayPath(p).toPath() }).toString(), err = true) }
            throw ProgramResult(1)
        }
    }
}
