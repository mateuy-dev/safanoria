package dev.mateuy.safanoria.core

import okio.Path

public sealed interface DiffResult {
    public data class Ready(
        val id: String,
        /** The ticket's branch: `<id>`, or `origin/<id>` for a ticket started on another computer. */
        val ref: String,
        val target: String,
        /** The merge-base of [ref] with [target]: the diff starts there, so what is new on the target doesn't show. */
        val base: String,
        /** Where branch `<id>` is checked out, or null. */
        val worktree: Path?,
        /** Where the diff shows more, or other, than the ticket's changes (as for [Review]). */
        val warnings: List<String>,
    ) : DiffResult

    public data class Refused(val reason: String) : DiffResult
}

/**
 * A quick look at a ticket's changes, without review mode: `git diff <target>...<id>`, or
 * `git difftool --dir-diff` for the user's diff tool, which copies only the changed files into
 * two temporary directories. Nothing in the ticket's worktree changes, so it works on any ticket
 * with a branch, while a session works in it, uncommitted changes and all.
 */
public object TicketDiff {
    /** With [uncommitted], the diff goes to the files in the ticket's worktree, so it needs one. */
    public fun prepare(repository: Repository, branches: Branches, id: String, uncommitted: Boolean = false): DiffResult {
        if (!isValidId(id)) return refused("'$id' is not a ticket id (SPEC §3)")
        val ticket = branches.graph.ticket(id) ?: return refused("no ticket '$id' on any branch")
        val git = repository.git
        return try {
            val ref = ticket.remoteBranch ?: id
            val tip = git.commitId("refs/heads/$id") ?: ticket.remoteBranch?.let { git.commitId("refs/remotes/$it") }
                ?: return refused("'$id' has no branch: it isn't started")
            val worktree = git.worktrees().firstOrNull { it.branch == id }?.path?.takeIf { repository.fileSystem.exists(it) }
                ?: Review.inReview(git, id)?.worktree
            if (uncommitted && worktree == null) return refused("branch '$id' isn't checked out in any worktree, so it has no uncommitted changes")
            val target = branches.targetBranch(id)
            val targetCommit = git.commitId("refs/heads/$target") ?: git.commitId(target) ?: return refused("no branch '$target', the target of '$id'")
            if (git.isAncestor(tip, targetCommit)) {
                return refused("'$id' is merged into $target already, so against it there is nothing to see: look at the merge commit instead (git log --merges $target)")
            }
            val base = git.mergeBase(targetCommit, tip) ?: return refused("'$id' and $target share no history")
            DiffResult.Ready(id, ref, target, base, worktree, Review.warnings(git, id, target, targetCommit, base, tip))
        } catch (e: GitException) {
            refused(e.message ?: e.toString())
        }
    }

    /**
     * The git command line that shows [ready], run from [root] (the repository): the diff, only its
     * stat with [stat], or in the diff tool with [tool]. With [uncommitted], it runs in the worktree
     * and goes up to its files (untracked ones aside) instead of the branch's last commit.
     */
    public fun command(root: Path, ready: DiffResult.Ready, tool: Boolean = false, stat: Boolean = false, uncommitted: Boolean = false): List<String> {
        val dir = if (uncommitted) checkNotNull(ready.worktree) { "uncommitted needs the worktree" } else root
        val action = if (tool) listOf("difftool", "--dir-diff") else listOf("diff") + if (stat) listOf("--stat") else emptyList()
        return listOf("git", "-C", dir.toString()) + action + ready.base + if (uncommitted) emptyList() else listOf(ready.ref)
    }

    /** Whether git has a diff tool set (`diff.tool`, or `merge.tool`, which difftool falls back to). Without one, it guesses. */
    public fun hasTool(git: Git): Boolean =
        listOf("diff.tool", "merge.tool").any { key -> runCatching { git.run("config", "--get", key).isNotBlank() }.getOrDefault(false) }

    private fun refused(reason: String) = DiffResult.Refused(reason)
}
