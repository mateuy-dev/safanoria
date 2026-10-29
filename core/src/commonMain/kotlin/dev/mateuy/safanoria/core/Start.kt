package dev.mateuy.safanoria.core

import okio.Path
import okio.Path.Companion.toPath

public sealed interface StartResult {
    public data class Ready(
        val id: String,
        /** The branch `<id>` is created from: the parent's branch, or mainBranch; for [Start.prepareCheckout], `origin/<id>`. */
        val base: String,
        /** Where to add the worktree (`worktree` in safanoria.yaml, `{id}` replaced). */
        val worktree: Path,
    ) : StartResult

    public data class Refused(val reason: String) : StartResult
}

/** Why [Start.begin] didn't start the ticket; [problems] when the started ticket wouldn't validate. */
public class StartException(message: String, public val problems: List<Diagnostic> = emptyList()) : Exception(message)

/**
 * Start (SPEC §11.2), the mechanical part: branch `<id>` from the parent's branch when its
 * children merge into it, else from mainBranch; its worktree; `status: in-progress` and a
 * `status · started` Work Log entry. [prepare] only checks and decides; [begin] creates the
 * branch with the ticket started on it; then the caller adds the worktree ([addWorktree]).
 * The CLI and apps share these.
 */
public object Start {
    public fun prepare(repository: Repository, branches: Branches, id: String): StartResult {
        if (!isValidId(id)) return refused("'$id' is not a ticket id (SPEC §3)")
        // The defaults that stand in for an unreadable safanoria.yaml don't say where the worktree goes.
        val config = repository.configResult.config
            ?: return refused(repository.configResult.diagnostics.joinToString("; ") { it.message }.ifEmpty { "$CONFIG_FILE can't be read" })
        val ticket = branches.graph.ticket(id) ?: return refused("no ticket '$id' on any branch")
        ticket.remoteBranch?.let { return refused("'$id' was started on another computer: it is on $it. Check out that branch here instead of starting it again") }
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

        val worktree = worktree(repository, config, id)
        if (repository.fileSystem.exists(worktree)) {
            return refused("the worktree path $worktree already exists")
        }
        return StartResult.Ready(id, base, worktree)
    }

    /**
     * For a ticket started on another computer (its real copy is on `origin/<id>` and there is no
     * local branch `<id>`): its worktree here, where [Start.prepare] would put it, with a local
     * branch `<id>` tracking the remote one. Nothing is committed: the ticket is started already.
     */
    public fun prepareCheckout(repository: Repository, branches: Branches, id: String): StartResult {
        if (!isValidId(id)) return refused("'$id' is not a ticket id (SPEC §3)")
        val config = repository.configResult.config
            ?: return refused(repository.configResult.diagnostics.joinToString("; ") { it.message }.ifEmpty { "$CONFIG_FILE can't be read" })
        val ticket = branches.graph.ticket(id) ?: return refused("no ticket '$id' on any branch")
        val remote = ticket.remoteBranch ?: return refused(
            if (repository.git.commitId("refs/heads/$id") != null) "branch '$id' is here already" else "'$id' isn't on a remote branch: start it instead",
        )
        val worktree = worktree(repository, config, id)
        if (repository.fileSystem.exists(worktree)) return refused("the worktree path $worktree already exists")
        return StartResult.Ready(id, remote, worktree)
    }

    /** Adds the worktree [prepareCheckout] decided on, creating local branch `<id>` to track the remote one. */
    public fun checkOut(repository: Repository, ready: StartResult.Ready) {
        repository.git.run("worktree", "add", "--track", "-b", ready.id, ready.worktree.toString(), ready.base)
    }

    /** Where ticket [id]'s worktree goes: `worktree` in safanoria.yaml, `{id}` replaced. */
    private fun worktree(repository: Repository, config: Config, id: String): Path {
        val path = config.worktree.replace("{id}", id).toPath()
        return (if (path.isAbsolute) path else repository.root / path).normalized()
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
        val problems = Validator(view.withFiles(listOf(file)), otherIds = Branches.ids(repository)).validate(listOf(file.path))
        if (problems.isNotEmpty()) throw StartException("the started ticket wouldn't be valid", problems)
        return view.commit(listOf(file), "$id: start")
    }

    /** Adds the worktree [prepare] decided on, with the started branch checked out. */
    public fun addWorktree(repository: Repository, ready: StartResult.Ready) {
        repository.git.run("worktree", "add", ready.worktree.toString(), ready.id)
    }

    private fun refused(reason: String) = StartResult.Refused(reason)
}

/**
 * The first prompt of a coding agent session opened on a started ticket's worktree (`start --herdr --prompt`).
 * [NONE] waits for the user; installed agent instructions provide ticket discovery.
 */
public enum class SessionPrompt(public val option: String) {
    NONE("none"),
    /** Read the ticket and discuss it with the user before changing anything. */
    CHAT("chat"),
    /** Work the ticket to completion and finish it. */
    IMPLEMENT("implement");

    /** The prompt for ticket [id]; null for [NONE]. */
    public fun text(id: String): String? = when (this) {
        NONE -> null
        CHAT -> "Let's discuss ticket $id before changing anything: what it asks, what's unclear or undecided, " +
            "and how you'd approach it. Don't edit files yet."
        IMPLEMENT -> "Implement ticket $id: work it to completion following the safanoria skill, then finish it."
    }
}
