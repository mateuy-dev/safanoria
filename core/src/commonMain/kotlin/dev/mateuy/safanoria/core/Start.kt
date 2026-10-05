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

/** Why [Start.begin] didn't start the ticket; [problems] when the started ticket wouldn't validate. */
public class StartException(message: String, public val problems: List<Diagnostic> = emptyList()) : Exception(message)

/**
 * Start (SPEC §11.2), the mechanical part: branch `<id>` from the parent's branch when its
 * children merge into it, else from mainBranch; the worktree when configured; `status:
 * in-progress` and a `status · started` Work Log entry. [prepare] only checks and decides;
 * [begin] creates the branch with the ticket started on it; then the caller adds the worktree
 * ([addWorktree]) or switches its checkout ([switchCheckout]). The CLI and apps share these.
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

    /**
     * Creates branch `<id>` and commits the ticket started on it, without checking it out, so
     * nothing in this checkout changes. Returns the commit id. A failure takes the branch back:
     * a half-started ticket is worse than none.
     */
    public fun begin(repository: Repository, ready: StartResult.Ready, today: String): String {
        val git = repository.git
        git.run("branch", ready.id, ready.base)
        try {
            return commitStarted(repository, ready.id, today)
        } catch (e: Exception) {
            runCatching { git.run("branch", "-D", ready.id) }
            throw e
        }
    }

    private fun commitStarted(repository: Repository, id: String, today: String): String {
        // The new branch isn't checked out anywhere, so the view reads its commit: the ticket as committed on the base.
        val view = BranchView.open(repository, id) ?: throw StartException("can't read the new branch '$id'.")
        val ticket = view.repository.ticket(id) ?: throw StartException("no ticket '$id' committed on the branch it starts from.")
        val text = try { edit(ticket.text, today) } catch (e: TicketEditException) {
            throw StartException(e.message ?: e.toString())
        }
        val file = PlannedFile(ticket.path, text, isNew = false)
        val problems = Validator(view.withFiles(listOf(file))).validate(listOf(file.path))
        if (problems.isNotEmpty()) throw StartException("the started ticket wouldn't be valid", problems)
        return view.commit(listOf(file), "$id: start")
    }

    /** Adds the worktree [prepare] decided on, with the started branch checked out. */
    public fun addWorktree(repository: Repository, ready: StartResult.Ready) {
        val worktree = ready.worktree ?: return
        repository.git.run("worktree", "add", worktree.toString(), ready.id)
    }

    /**
     * Without a worktree: switches this checkout to the started branch, unless it has uncommitted
     * changes that would come along. Returns null when switched, else why not.
     */
    public fun switchCheckout(repository: Repository, id: String): String? {
        val git = repository.git
        val dirty = git.run("status", "--porcelain", "--untracked-files=no").lines().any { it.isNotBlank() }
        if (dirty) return "this checkout has uncommitted changes, so it stays where it is; switch with: git switch $id"
        return try {
            git.run("switch", "-q", id)
            null
        } catch (e: GitException) {
            "couldn't switch to $id (${e.message}); switch with: git switch $id"
        }
    }

    private fun refused(reason: String) = StartResult.Refused(reason)
}
