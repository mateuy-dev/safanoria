package dev.mateuy.safanoria.core

import okio.Path

/**
 * A worktree in review mode: detached at [base] with ticket [id]'s changes staged on top. [tip]
 * is where branch `<id>` was when the review started, [target] what [base] was taken from, and
 * [since] the commit given instead of the merge-base, if any.
 */
public data class ReviewState(val worktree: Path, val id: String, val target: String, val base: String, val tip: String, val since: String?)

public sealed interface ReviewResult {
    public data class Ready(
        val id: String,
        /** The worktree where branch `<id>` is checked out: review mode happens there. */
        val worktree: Path,
        val target: String,
        val base: String,
        val tip: String,
        val since: String?,
        /** Where the diff from [base] shows more, or other, than the ticket's changes. */
        val warnings: List<String>,
    ) : ReviewResult

    public data class Refused(val reason: String) : ReviewResult
}

/** What [Review.end] did: [edits] are the human's changes, left uncommitted on the branch; [moved] when the branch moved meanwhile. */
public data class ReviewEnded(val id: String, val worktree: Path, val edits: List<String>, val moved: Boolean)

public class ReviewException(message: String) : Exception(message)

/**
 * Review mode: the ticket's whole diff as uncommitted changes in its worktree, for an IDE to show
 * (changed files, gutter marks) on the real code. [begin] detaches HEAD and moves it to the
 * merge-base with the target (`reset --soft`), so branch `<id>` stays where it is; [end] moves
 * HEAD back to the branch's commit and switches to it, which leaves exactly the edits made
 * during the review uncommitted, even if they were committed meanwhile. Never `reset --hard`.
 *
 * The state is a marker file in the worktree's own git directory, so anything (a fresh session,
 * `merge`, `finish`) can tell: a detached worktree that doesn't show its branch in `git worktree list`.
 */
public object Review {
    internal const val MARKER: String = "safanoria-review"

    public fun prepare(repository: Repository, branches: Branches, id: String, since: String? = null): ReviewResult {
        if (!isValidId(id)) return refused("'$id' is not a ticket id (SPEC §3)")
        val ticket = branches.graph.ticket(id) ?: return refused("no ticket '$id' on any branch")
        Finish.onlyRemote(ticket)?.let { return refused(it) }
        val git = repository.git
        return try {
            inReview(git, id)?.let { return refused("${it.worktree} is in review mode for '$id' already: end it first (safanoria-cli review --end)") }
            val tip = git.commitId("refs/heads/$id") ?: return refused("no local branch '$id'")
            val worktree = git.worktrees().firstOrNull { it.branch == id }?.path?.takeIf { repository.fileSystem.exists(it) }
                ?: return refused("branch '$id' isn't checked out in any worktree; review mode works in the ticket's worktree (git worktree add <path> $id)")
            val changes = Git(worktree).uncommitted()
            if (changes.isNotEmpty()) {
                return refused("$worktree has uncommitted changes (${files(changes.size)}): commit or discard them first, or they'd be mixed into the review")
            }
            val target = branches.targetBranch(id)
            val targetCommit = git.commitId("refs/heads/$target") ?: git.commitId(target) ?: return refused("no branch '$target', the target of '$id'")
            if (git.isAncestor(tip, targetCommit)) {
                return refused("'$id' is merged into $target already, so against it there is nothing to see: review the merge commit instead (git log --merges $target)")
            }
            val mergeBase = git.mergeBase(targetCommit, tip) ?: return refused("'$id' and $target share no history")
            val base = if (since == null) mergeBase else {
                val commit = git.commitId(since) ?: return refused("no commit '$since'")
                if (!git.isAncestor(commit, tip)) return refused("$since isn't on branch '$id'")
                if (commit == tip) return refused("nothing on '$id' after $since")
                commit
            }
            ReviewResult.Ready(id, worktree, target, base, tip, since, warnings(git, id, target, targetCommit, base, tip))
        } catch (e: GitException) {
            refused(e.message ?: e.toString())
        }
    }

    /**
     * Commits after [base] that don't descend from it and aren't in the target: brought by a merge
     * from elsewhere (a child merged main, its parent didn't). The diff from [base] has their changes too.
     */
    internal fun warnings(git: Git, id: String, target: String, targetCommit: String, base: String, tip: String): List<String> {
        val all = git.count(tip, "^$base", "^$targetCommit")
        val descending = git.count("--ancestry-path=$base", tip, "^$base", "^$targetCommit")
        val foreign = all - descending
        if (foreign <= 0) return emptyList()
        return listOf(
            "'$id' merged in ${commits(foreign)} that $target doesn't have (e.g. main, merged into '$id' but not into $target): " +
                "their changes show as the ticket's. Merging them into $target first (and $target into '$id') leaves only the ticket's",
        )
    }

    /**
     * Enters review mode in [Ready.worktree]: detaches HEAD, writes the marker, moves HEAD to the
     * base. Returns what is now staged, as `git diff --cached --stat`.
     */
    public fun begin(ready: ReviewResult.Ready): String {
        val git = Git(ready.worktree)
        git.run("checkout", "-q", "--detach")
        // Before the reset: from here on the worktree is in review mode, and end undoes it.
        val marker = buildString {
            append("id ${ready.id}\ntarget ${ready.target}\nbase ${ready.base}\ntip ${ready.tip}\n")
            ready.since?.let { append("since $it\n") }
        }
        SystemFileSystem.write(git.gitDir() / MARKER) { writeUtf8(marker) }
        git.run("reset", "-q", "--soft", ready.base)
        return git.stagedStat()
    }

    /**
     * Leaves review mode in [worktree]: HEAD back to the branch's commit at the start (`reset --soft`),
     * then on the branch. What differs from it then is the human's edits, kept uncommitted.
     */
    public fun end(worktree: Path): ReviewEnded {
        val git = Git(worktree)
        val state = try { state(worktree) } catch (e: GitException) { throw ReviewException(e.message ?: e.toString()) }
            ?: throw ReviewException(notInReview(git))
        try {
            git.run("reset", "-q", "--soft", state.tip)
            val edits = git.uncommitted()
            val moved = git.commitId("refs/heads/${state.id}") != state.tip
            // A branch moved meanwhile: switch carries the edits over, or refuses when they'd conflict.
            git.run("switch", "-q", state.id)
            SystemFileSystem.delete(git.gitDir() / MARKER)
            return ReviewEnded(state.id, worktree, edits, moved)
        } catch (e: GitException) {
            throw ReviewException("still in review mode, HEAD detached at ${state.tip.take(7)} with the edits staged: ${e.message}")
        }
    }

    private fun notInReview(git: Git): String {
        val branch = git.currentBranch()
        return if (branch == "HEAD") "this worktree has HEAD detached, but not by safanoria-cli review: nothing to end"
        else "not in review mode: on branch '$branch'"
    }

    /** Review mode of the worktree at [worktree], or null: its marker, while HEAD is detached. */
    public fun state(worktree: Path): ReviewState? {
        val git = Git(worktree)
        val marker = git.gitDir() / MARKER
        if (!SystemFileSystem.exists(marker) || git.currentBranch() != "HEAD") return null
        val fields = SystemFileSystem.read(marker) { readUtf8() }.lines()
            .mapNotNull { line -> line.split(' ', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1].trim() } }
            .toMap()
        return ReviewState(
            worktree,
            fields["id"] ?: return null,
            fields["target"] ?: return null,
            fields["base"] ?: return null,
            fields["tip"] ?: return null,
            fields["since"],
        )
    }

    /** The worktree in review mode for ticket [id], or null. Only detached worktrees can be. */
    public fun inReview(git: Git, id: String): ReviewState? =
        git.worktrees()
            .filter { it.branch == null && SystemFileSystem.exists(it.path) }
            .firstNotNullOfOrNull { w -> runCatching { state(w.path) }.getOrNull()?.takeIf { it.id == id } }

    /** Why nothing may commit on [branch] now, or null: it is in review mode, where [end] needs it unmoved. */
    public fun blocking(git: Git, branch: String): String? =
        runCatching { inReview(git, branch) }.getOrNull()
            ?.let { "'$branch' is in review mode in ${it.worktree}: end it first (safanoria-cli review --end)" }

    private fun files(n: Int) = "$n file${if (n == 1) "" else "s"}"
    private fun commits(n: Int) = "$n commit${if (n == 1) "" else "s"}"
    private fun refused(reason: String) = ReviewResult.Refused(reason)
}
