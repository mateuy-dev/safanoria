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
import dev.mateuy.safanoria.core.Repository
import dev.mateuy.safanoria.core.Status
import dev.mateuy.safanoria.core.text
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import okio.Path.Companion.toPath
import kotlin.time.Clock

/**
 * `safanoria finish <id> [--done]`: SPEC §11.4 Finish. Sets the status on the ticket's real copy
 * (SPEC §14.1) and commits only that file there: in the worktree that has the branch checked
 * out, else on the branch without checking it out.
 */
class FinishCommand : RepositoryCommand(name = "finish") {
    override fun help(context: Context) =
        "Finish a ticket: set status: review (work complete), or with --done set done once its branch is merged, " +
            "and log it. Commits only the ticket, on the branch that has its real copy."

    private val idArgument by argument(name = "id", help = "Ticket id (on a terminal: asks, starting on this branch's ticket)").optional()
    private val done by option("--done", help = "Set done: the branch is merged into its target (also checks the parent's Plan item)").flag()
    private val dryRun by option("--dry-run", help = "Show what would be done, change nothing").flag()
    private val date by option("--date", hidden = true, help = "Today's date (tests)")

    private lateinit var id: String

    override fun run() {
        val repo = repository
        val today = date ?: Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        val status = if (done) Status.DONE else Status.REVIEW
        val branches = Branches.read(repo) ?: throw PrintMessage(
            "Not finished: needs a git repository with the branch '${repo.config.mainBranch}' (mainBranch).", 1, true,
        )
        id = idArgument ?: askFinish(repo, branches, status)
        val ready = when (val r = Finish.prepare(branches, id, status)) {
            is FinishResult.Refused -> throw PrintMessage("Not finished: ${r.reason}", 1, true)
            is FinishResult.Ready -> r
        }
        val verb = if (dryRun) "would " else ""
        echo("${verb}set status: ${status.text} and log it, committed on ${ready.branch}")
        val parent = ready.parent
        if (parent != null) echo("${verb}check '$id' in the Plan of ${parent.fileId}, committed on ${ready.parentBranch}")
        if (dryRun) return

        try {
            Finish.perform(repo, ready, today) { echo("committed ${it.commit.take(7)} on ${it.branch}: ${it.message}") }
        } catch (e: FinishException) {
            if (e.problems.isEmpty()) throw PrintMessage("Not finished: ${e.message}", 1, true)
            e.problems.forEach { echo(it.copy(file = it.file?.let { p -> displayPath(p).toPath() }).toString(), err = true) }
            throw ProgramResult(1)
        }
    }

    /** review: in-progress tickets; done: in review. Starts on this branch's ticket when it's one of them. */
    private fun askFinish(repo: Repository, branches: Branches, status: Status): String {
        val from = if (status == Status.DONE) setOf(Status.REVIEW) else setOf(Status.IN_PROGRESS)
        val current = runCatching { repo.git.currentBranch() }.getOrNull()
        return askTicket(branches, "Set which ticket to ${status.text}?", from, "nothing to finish: no ${from.single().text} ticket", default = current)
    }

}
