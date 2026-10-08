package dev.mateuy.safanoria.core

import okio.Path

public sealed interface LandResult {
    public data class Ready(
        val id: String,
        /** The branch it merges into (SPEC §14.1). */
        val target: String,
        /** The commits merged: [target]'s and branch `<id>`'s when [Land.prepare] looked. */
        val targetCommit: String,
        val branchCommit: String,
        /** The merge of both, before the ticket is set `done` in it. */
        val tree: String,
        /** The parent whose Plan item for this ticket gets checked, and its branch. */
        val parent: Ticket?,
        val parentBranch: String?,
    ) : LandResult

    public data class Refused(val reason: String) : LandResult
}

/** Why [Land.perform] didn't land the ticket; [problems] when the merged tickets wouldn't validate. */
public class LandException(message: String, public val problems: List<Diagnostic> = emptyList()) : Exception(message)

/** What [Land.perform] did: the merge [commit] on [target], and what happened to the worktree and the branch. */
public data class Landed(val target: String, val commit: String, val message: String, val cleanUp: List<String>)

/**
 * Lands a ticket in review (SPEC §11.5): merges branch `<id>` into its target with a merge
 * commit that also sets `done`, logs it and checks the parent's Plan item, so `done` and the
 * merge can't differ; then removes the worktree and deletes the branch ([cleanUp]). Nothing is
 * pushed. [prepare] checks and merges the trees without touching anything; [perform] makes the
 * commit and moves the target, or changes nothing. The CLI and apps share these.
 */
public object Land {
    public fun prepare(repository: Repository, branches: Branches, id: String): LandResult {
        if (!isValidId(id)) return refused("'$id' is not a ticket id (SPEC §3)")
        val ticket = branches.graph.ticket(id) ?: return refused("no ticket '$id' on any branch")
        val status = ticket.frontmatter?.status
        val target = branches.targetBranch(id)
        if (status != Status.REVIEW) {
            val next = if (status == Status.IN_PROGRESS) ": set it to review first (finish), which checks it is ready" else ""
            return refused("'$id' is ${status?.text ?: "without a readable status"}; only a ticket in review is merged$next")
        }
        if (ticket.branch != id) {
            return refused("'$id' is merged into ${ticket.branch} already: set it done with finish --done")
        }
        val git = repository.git
        return try {
            val branchCommit = git.commitId("refs/heads/$id") ?: return refused("no local branch '$id' to merge")
            val targetCommit = git.commitId("refs/heads/$target") ?: return refused("no local branch '$target' to merge into")
            val worktree = git.worktrees().firstOrNull { it.branch == id }?.path?.takeIf { repository.fileSystem.exists(it) }
            val changes = worktree?.let { Git(it).uncommitted() }.orEmpty()
            if (changes.isNotEmpty()) {
                return refused("$worktree has uncommitted changes (${changes.size} file${if (changes.size == 1) "" else "s"}): commit or discard them first")
            }
            when (val merge = git.mergeTree(targetCommit, branchCommit)) {
                is MergeTree.Conflict -> refused(
                    "merging '$id' into $target conflicts in ${merge.paths.joinToString()}: " +
                        "merge $target into '$id' and solve it there, then merge again",
                )
                is MergeTree.Clean -> {
                    val parent = Finish.uncheckedParent(branches, ticket)
                    LandResult.Ready(id, target, targetCommit, branchCommit, merge.tree, parent, parent?.branch)
                }
            }
        } catch (e: GitException) {
            refused(e.message ?: e.toString())
        }
    }

    /**
     * Commits the merge with the ticket `done` in it and moves the target there: by a
     * fast-forward where the target is checked out (which refuses when uncommitted changes are
     * in the way), else by moving the branch. Until then nothing has changed, so any failure
     * leaves things as they were. Then [cleanUp].
     */
    public fun perform(repository: Repository, ready: LandResult.Ready, today: String): Landed {
        val git = repository.git
        val id = ready.id
        val layout = Branches.layout(repository, git.worktrees()) ?: throw LandException("can't tell where this checkout is in git.")
        val merged = Repository(repository.root, GitTreeFileSystem(git, ready.tree, layout.top))
        val parentInMerge = ready.parent?.takeIf { ready.parentBranch == ready.target }

        fun edited(ticketId: String, change: (Ticket) -> String): PlannedFile {
            val ticket = merged.ticket(ticketId) ?: throw LandException("the merge has no ticket '$ticketId'.")
            val text = try { change(ticket) } catch (e: TicketEditException) { throw LandException(e.message ?: e.toString()) }
            return PlannedFile(ticket.path, text, isNew = false)
        }
        val files = listOfNotNull(
            edited(id) { Finish.edit(it.text, Status.DONE, today) },
            parentInMerge?.let { p -> edited(p.fileId) { Finish.checkInParent(it.text, it.path, id, today) } },
        )

        // The tickets the merge brings to the target, and the ones changed here: those must be valid there.
        val result = Repository(repository.root, OverlayFileSystem(merged.fileSystem, files.associate { it.path.normalized() to it.text }))
        val brought = git.changedPaths(ready.targetCommit, ready.tree, repository.config.dir).map { repository.root / it }
            .filter { result.fileSystem.exists(it) }
        val problems = Validator(result).validate(brought + files.map { it.path }).filter { it.severity == Severity.ERROR }
        if (problems.isNotEmpty()) throw LandException("the merged tickets wouldn't be valid", problems)

        val message = "$id: merge (done)"
        try {
            val relative = files.map { it.path.relativeTo(repository.root).segments.joinToString("/") to it.text }
            val commit = git.commitTree(treeWith(git, ready.tree, layout.sub, relative), listOf(ready.targetCommit, ready.branchCommit), message)
            val checkedOut = git.worktrees().firstOrNull { it.branch == ready.target }?.path?.takeIf { repository.fileSystem.exists(it) }
            if (checkedOut != null) Git(checkedOut).run("merge", "-q", "--ff-only", commit)
            else git.updateBranch(ready.target, commit, ready.targetCommit, "safanoria-cli merge")

            val notes = mutableListOf<String>()
            val parent = ready.parent
            if (parent != null && parentInMerge == null) notes += checkParentElsewhere(repository, parent, ready.parentBranch!!, id, today)
            return Landed(ready.target, commit, message, notes + cleanUp(repository, id, ready.target))
        } catch (e: GitException) {
            throw LandException("nothing changed: ${e.message}")
        }
    }

    /** A parent whose real copy isn't on the target (`childrenMergeInto: main`, parent unmerged) gets its own commit. */
    private fun checkParentElsewhere(repository: Repository, parent: Ticket, branch: String, id: String, today: String): String = try {
        val change = TicketChange(parent.fileId) { text, path -> Finish.checkInParent(text, path, id, today) }
        val c = Finish.commit(repository, branch, listOf(change), "$id: done")
        "checked '$id' in the Plan of ${parent.fileId}: ${c.commit.take(7)} on $branch"
    } catch (e: Exception) {
        "couldn't check '$id' in the Plan of ${parent.fileId} on $branch: ${e.message}"
    }

    /**
     * After the merge: removes the worktree of branch [id] and deletes the branch, and says what
     * it did. What it can't do safely it leaves, saying why: a worktree with uncommitted changes
     * (nothing of the user's is thrown away), a branch that isn't merged into [target], a branch
     * checked out in the main checkout (which is never a ticket's worktree).
     */
    public fun cleanUp(repository: Repository, id: String, target: String): List<String> {
        val out = mutableListOf<String>()
        try {
            val worktrees = repository.git.worktrees()
            // From the main checkout: the directory this runs in may be the one removed.
            val main = worktrees.first()
            val git = Git(main.path)
            val worktree = worktrees.firstOrNull { it.branch == id }
            var free = worktree == null
            if (worktree != null) free = try {
                freeBranch(git, worktree, isMain = worktree == main, out)
            } catch (e: GitException) {
                out += "kept ${worktree.path}: ${e.message}"
                false
            }
            if (free && git.commitId("refs/heads/$id") != null) {
                if (git.commitId("refs/heads/$target") == null || !git.isAncestor("refs/heads/$id", "refs/heads/$target")) {
                    out += "kept the branch '$id': it has commits that aren't in $target"
                } else {
                    git.run("branch", "-q", "-D", id)
                    out += "deleted the branch '$id'"
                }
            }
        } catch (e: GitException) {
            out += "clean-up stopped: ${e.message}"
        }
        return out
    }

    /** Makes [worktree] stop holding its branch; true when it did. */
    private fun freeBranch(git: Git, worktree: Worktree, isMain: Boolean, out: MutableList<String>): Boolean {
        val path: Path = worktree.path
        if (SystemFileSystem.exists(path) && Git(path).uncommitted().isNotEmpty()) {
            out += "kept $path and its branch: it has uncommitted changes"
            return false
        }
        if (isMain) {
            out += "kept the branch '${worktree.branch}': it is checked out in the main checkout $path"
            return false
        }
        git.run("worktree", "remove", path.toString())
        out += "removed the worktree $path"
        return true
    }

    private fun refused(reason: String) = LandResult.Refused(reason)
}
