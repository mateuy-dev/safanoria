package dev.mateuy.safanoria.core

import okio.Path
import okio.Path.Companion.toPath

public sealed interface StartResult {
    public data class Ready(
        val id: String,
        /** The branch `<id>` is created from: the parent's branch, or mainBranch. */
        val base: String,
        /** Where to add the worktree (`worktree` in safanoria.yaml, `{id}` replaced), or null. */
        val worktree: Path?,
    ) : StartResult

    public data class Refused(val reason: String) : StartResult
}

/**
 * Start (SPEC §11.2), the mechanical part: branch `<id>` from the parent's branch when its
 * children merge into it, else from mainBranch; the worktree when configured; `status:
 * in-progress` and a `status · started` Work Log entry. [prepare] only checks and decides; the
 * caller creates the branch and applies [edit] to the ticket as it is there.
 */
public object Start {
    public fun prepare(repository: Repository, branches: Branches, id: String): StartResult {
        if (!isValidId(id)) return refused("'$id' is not a ticket id (SPEC §3)")
        val ticket = branches.graph.ticket(id) ?: return refused("no ticket '$id' on any branch")
        val git = repository.git
        if (git.commitId("refs/heads/$id") != null) {
            return refused("branch '$id' already exists: it was started already")
        }
        val status = ticket.frontmatter?.status
        if (status != Status.BACKLOG && status != Status.READY) {
            return refused("'$id' is ${status?.text ?: "without a readable status"}; only backlog and ready tickets can be started")
        }

        val parent = branches.graph.parent(ticket)
        if (parent != null && parent.frontmatter?.childrenMergeInto != "main" && git.commitId("refs/heads/${parent.fileId}") == null) {
            return refused("its parent '${parent.fileId}' isn't started: children branch from the parent's branch (SPEC §8.1); start '${parent.fileId}' first")
        }
        val base = branches.targetBranch(id)
        if (git.commitId("refs/heads/$base") == null) return refused("no local branch '$base' to start from")

        val worktree = repository.config.worktree?.let { setting ->
            val path = setting.replace("{id}", id).toPath()
            (if (path.isAbsolute) path else repository.root / path).normalized()
        }
        if (worktree != null && repository.fileSystem.exists(worktree)) {
            return refused("the worktree path $worktree already exists")
        }
        return StartResult.Ready(id, base, worktree)
    }

    /** [text] (the ticket on its new branch) started: `in-progress`, `updated`, and the Work Log entry. */
    public fun edit(text: String, today: String): String =
        TicketEditor(text)
            .setField("status", Status.IN_PROGRESS.text)
            .setField("updated", today)
            .appendWorkLog(today, "status", "started")
            .text

    private fun refused(reason: String) = StartResult.Refused(reason)
}
