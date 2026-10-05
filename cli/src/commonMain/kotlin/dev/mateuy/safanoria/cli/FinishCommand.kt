package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import dev.mateuy.safanoria.core.Branches
import dev.mateuy.safanoria.core.Finish
import dev.mateuy.safanoria.core.FinishException
import dev.mateuy.safanoria.core.FinishResult
import dev.mateuy.safanoria.core.GitException
import dev.mateuy.safanoria.core.Land
import dev.mateuy.safanoria.core.Repository
import dev.mateuy.safanoria.core.Status
import dev.mateuy.safanoria.core.text
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import okio.Path.Companion.toPath
import kotlin.time.Clock

/**
 * `safanoria-cli finish <id> [--done]`: SPEC §11.4 Finish. Sets the status on the ticket's real copy
 * (SPEC §14.1) and commits only that file there: in the worktree that has the branch checked
 * out, else on the branch without checking it out. `--done` without an id is for merges made
 * elsewhere: every ticket merged but still in review.
 */
class FinishCommand : RepositoryCommand(name = "finish") {
    override fun help(context: Context) =
        "Finish a ticket: set status: review (work complete) once its branch has everything committed and isn't behind " +
            "its target, and log it. Commits only the ticket, on the branch that has its real copy. Then `merge` lands it. " +
            "With --done, for a branch merged elsewhere (a pull request): set done; without an id, every ticket " +
            "merged but still in review, removing their worktrees and branches."

    private val idArgument by argument(name = "id", help = "Ticket id (on a terminal: asks, starting on this branch's ticket; with --done: every merged ticket in review)").optional()
    private val done by option("--done", help = "Set done: the branch is merged into its target (also checks the parent's Plan item)").flag()
    private val dryRun by option("--dry-run", help = "Show what would be done, change nothing").flag()
    private val date by option("--date", hidden = true, help = "Today's date (tests)")

    override fun run() {
        val repo = repository
        val today = date ?: Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        val status = if (done) Status.DONE else Status.REVIEW
        val branches = Branches.read(repo) ?: throw PrintMessage(
            "Not finished: needs a git repository with the branch '${repo.config.mainBranch}' (mainBranch).", 1, true,
        )
        val given = idArgument
        if (done && given == null) {
            val merged = Finish.merged(branches)
            if (merged.isEmpty()) echo("nothing to set done: no ticket in review is merged into its target")
            // Read again after each: a commit moves the branch the next ticket is read from.
            merged.forEach { finish(repo, Branches.read(repo) ?: branches, it, status, today) }
            return
        }
        finish(repo, branches, given ?: askFinish(repo, branches), status, today)
    }

    private fun finish(repo: Repository, branches: Branches, id: String, status: Status, today: String) {
        val ready = when (val r = Finish.prepare(repo, branches, id, status)) {
            is FinishResult.Refused -> throw PrintMessage("Not finished: ${r.reason}", 1, true)
            is FinishResult.Ready -> r
        }
        val verb = if (dryRun) "would " else ""
        echo("${verb}set '$id' to ${status.text} and log it, committed on ${ready.branch}")
        val parent = ready.parent
        if (parent != null) echo("${verb}check '$id' in the Plan of ${parent.fileId}, committed on ${ready.parentBranch}")
        if (ready.open.isNotEmpty()) {
            echo("still open in the ticket (not blocking):")
            ready.open.forEach { echo("  $it") }
        }
        if (dryRun) return

        try {
            Finish.perform(repo, ready, today) { echo("committed ${it.commit.take(7)} on ${it.branch}: ${it.message}") }
        } catch (e: FinishException) {
            if (e.problems.isEmpty()) throw PrintMessage("Not finished: ${e.message}", 1, true)
            e.problems.forEach { echo(it.copy(file = it.file?.let { p -> displayPath(p).toPath() }).toString(), err = true) }
            throw ProgramResult(1)
        }
        if (status == Status.DONE) {
            Land.cleanUp(repo, id, ready.target).forEach { echo(it) }
        } else if (ready.branch != ready.target) {
            val stat = try { repo.git.diffStat(ready.target, ready.branch) } catch (e: GitException) { "" }
            if (stat.isNotEmpty()) echo("to review, against ${ready.target} (git diff ${ready.target}...${ready.branch}):\n$stat")
            echo("next: safanoria-cli merge $id to land it, or safanoria-cli reopen $id --reason \"…\" to go back")
        }
    }

    /** The in-progress tickets, starting on this branch's ticket when it's one of them. */
    private fun askFinish(repo: Repository, branches: Branches): String {
        val current = runCatching { repo.git.currentBranch() }.getOrNull()
        return askTicket(branches, "Set which ticket to review?", setOf(Status.IN_PROGRESS), "nothing to finish: no in-progress ticket", default = current)
    }
}
