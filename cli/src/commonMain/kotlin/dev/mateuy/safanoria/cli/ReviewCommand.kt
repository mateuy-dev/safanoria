package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import dev.mateuy.safanoria.core.Branches
import dev.mateuy.safanoria.core.GitException
import dev.mateuy.safanoria.core.Review
import dev.mateuy.safanoria.core.ReviewException
import dev.mateuy.safanoria.core.ReviewResult
import dev.mateuy.safanoria.core.Status
import okio.Path

/**
 * `safanoria-cli review <id> [--since <commit>]` and `review --end`: review mode (see [Review]),
 * the ticket's changes as uncommitted ones in its worktree for an IDE to show.
 */
class ReviewCommand : RepositoryCommand(name = "review") {
    override fun help(context: Context) =
        "Review mode: in the ticket's worktree, detach HEAD at the merge-base with its target (main, or the parent's " +
            "branch) so the whole ticket shows as staged changes in an IDE; the branch doesn't move. --end goes back to " +
            "the branch, leaving only the edits made during the review uncommitted. Refuses on uncommitted changes."

    private val idArgument by argument(name = "id", help = "Ticket id (on a terminal: asks, starting on this branch's ticket; with --end: the ticket whose review to end, default this worktree's)").optional()
    private val end by option("--end", help = "Leave review mode, keeping your edits as uncommitted changes on the branch").flag()
    private val since by option("--since", help = "Review only what changed after this commit (e.g. the fixes after a first review)")
    private val dryRun by option("--dry-run", help = "Show what would be done, change nothing").flag()

    override fun run() {
        if (end) return end()
        val repo = repository
        val branches = Branches.read(repo) ?: throw PrintMessage(
            "No review mode: needs a git repository with the branch '${repo.config.mainBranch}' (mainBranch).", 1, true,
        )
        val id = idArgument ?: askTicket(
            branches, "Review which ticket?", setOf(Status.IN_PROGRESS, Status.REVIEW), "nothing to review: no ticket in progress or in review",
            default = runCatching { repo.git.currentBranch() }.getOrNull(),
        )
        val ready = when (val r = Review.prepare(repo, branches, id, since)) {
            is ReviewResult.Refused -> throw PrintMessage("No review mode: ${r.reason}", 1, true)
            is ReviewResult.Ready -> r
        }
        val against = ready.since?.let { "after $it" } ?: "against ${ready.target}"
        ready.warnings.forEach { echo("warning: $it", err = true) }
        if (dryRun) {
            echo("would detach ${displayPath(ready.worktree)} at ${ready.base.take(7)} with '$id' $against staged; branch '$id' stays at ${ready.tip.take(7)}")
            return
        }
        val stat = try { Review.begin(ready) } catch (e: GitException) {
            throw PrintMessage("Review mode not entered completely: ${e.message}\nBack to the branch with: git -C ${ready.worktree} switch $id", 1, true)
        }
        echo("review mode in ${displayPath(ready.worktree)}: '$id' $against is staged, HEAD detached at ${ready.base.take(7)}; branch '$id' stays at ${ready.tip.take(7)}")
        if (stat.isNotEmpty()) echo(stat)
        echo("your edits stay yours; when done: safanoria-cli review --end")
    }

    private fun end() {
        val worktree: Path = idArgument?.let { id ->
            runCatching { Review.inReview(repository.git, id) }.getOrNull()?.worktree
                ?: throw PrintMessage("Not ended: '$id' isn't in review mode in any worktree", 1, true)
        } ?: repository.root // git finds the worktree it is in
        val ended = try { Review.end(worktree) } catch (e: ReviewException) {
            throw PrintMessage("Not ended: ${e.message}", 1, true)
        }
        echo("review mode ended in ${displayPath(ended.worktree)}: back on branch '${ended.id}'")
        if (ended.moved) echo("the branch moved during the review: your edits were carried over to its new commit")
        if (ended.edits.isEmpty()) echo("no edits made during the review")
        else {
            echo("edits made during the review, uncommitted (${ended.edits.size}):")
            ended.edits.forEach { echo("  $it") }
        }
    }
}
