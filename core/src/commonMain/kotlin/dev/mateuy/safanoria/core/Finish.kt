package dev.mateuy.safanoria.core

public sealed interface FinishResult {
    public data class Ready(
        val id: String,
        val status: Status,
        /** The branch holding the ticket's real copy (SPEC §14.1), where the change is committed. */
        val branch: String,
        /** For `done`: the parent whose Plan item for this ticket gets checked, and its branch. */
        val parent: Ticket?,
        val parentBranch: String?,
        /** The branch it merges into (SPEC §14.1). */
        val target: String = branch,
        /** For `review`: what is still open in the ticket. Listed, never blocking (SPEC §7.6). */
        val open: List<String> = emptyList(),
    ) : FinishResult

    public data class Refused(val reason: String) : FinishResult
}

/** Why [Finish.perform] didn't finish the ticket; [problems] when the changed tickets wouldn't validate. */
public class FinishException(message: String, public val problems: List<Diagnostic> = emptyList()) : Exception(message)

/** A commit [Finish.perform] made. */
public data class FinishCommit(val branch: String, val commit: String, val message: String)

/** A ticket file to change on a branch: [change] gets its text and path there. */
internal class TicketChange(val ticketId: String, val change: (String, okio.Path) -> String)

/**
 * Finish (SPEC §11.4, §11.5): `review` when the work is complete, `done` once merged into its target.
 * [prepare] only checks and decides where each copy is; [perform] applies [edit] and
 * [checkInParent] there and commits. The CLI and apps share these. [Land] does both the merge
 * and `done`; this `done` is for merges made elsewhere ([merged]).
 */
public object Finish {
    public fun prepare(repository: Repository, branches: Branches, id: String, status: Status): FinishResult {
        require(status == Status.REVIEW || status == Status.DONE) { "finish sets review or done" }
        if (!isValidId(id)) return FinishResult.Refused("'$id' is not a ticket id (SPEC §3)")
        val ticket = branches.graph.ticket(id) ?: return FinishResult.Refused("no ticket '$id' on any branch")
        val current = ticket.frontmatter?.status
        val from = if (status == Status.REVIEW) setOf(Status.IN_PROGRESS) else setOf(Status.IN_PROGRESS, Status.REVIEW)
        if (current !in from) {
            return FinishResult.Refused("'$id' is ${current?.text ?: "without a readable status"}; ${status.text} needs ${from.joinToString(" or ") { it.text }}")
        }
        val branch = ticket.branch ?: return FinishResult.Refused("can't tell which branch has '$id'")
        val target = branches.targetBranch(id)
        if (status == Status.DONE && branch == id) {
            return FinishResult.Refused("branch '$id' isn't merged into $target yet: done means merged (SPEC §6.1)")
        }
        if (status == Status.REVIEW) notReviewable(repository, branch, target)?.let { return FinishResult.Refused(it) }
        val parent = if (status == Status.DONE) uncheckedParent(branches, ticket) else null
        val open = if (status == Status.REVIEW) openItems(ticket) else emptyList()
        return FinishResult.Ready(id, status, branch, parent, parent?.branch, target, open)
    }

    /**
     * Null when what is on [branch] is what a reviewer should see, else why not: uncommitted
     * changes where it is checked out, or commits of [target] it doesn't have. Commits that only
     * touch tickets don't count: tickets are created on the main branch all the time (SPEC §14.2).
     */
    private fun notReviewable(repository: Repository, branch: String, target: String): String? {
        if (branch == target) return null
        val git = repository.git
        try {
            val worktree = git.worktrees().firstOrNull { it.branch == branch }?.path?.takeIf { repository.fileSystem.exists(it) }
            val changes = worktree?.let { Git(it).uncommitted() }.orEmpty()
            if (changes.isNotEmpty()) {
                return "$worktree has uncommitted changes (${files(changes.size)}): commit or discard them first, review is of what is committed"
            }
            if (git.commitId("refs/heads/$branch") == null || git.commitId("refs/heads/$target") == null) return null
            val behind = git.behind(branch, target, repository.config.dir)
            if (behind > 0) {
                return "branch '$branch' is behind $target by $behind commit${if (behind == 1) "" else "s"}: " +
                    "merge $target into it (or rebase) first, so what is reviewed is what will be merged"
            }
        } catch (e: GitException) {
            return e.message
        }
        return null
    }

    private fun files(n: Int) = "$n file${if (n == 1) "" else "s"}"

    /** Unchecked Acceptance Criteria and pending Learnings of [ticket], one line each. */
    public fun openItems(ticket: Ticket): List<String> =
        ticket.body.checklist("Acceptance Criteria").filter { !it.checked }.map { "Acceptance Criteria, unchecked: ${it.text}" } +
            ticket.body.learnings.filter { it.resolution == Resolution.Pending }.map { "Learning, pending: ${it.text}" }

    /** The parent of [ticket] when its Plan item for it is still unchecked. */
    internal fun uncheckedParent(branches: Branches, ticket: Ticket): Ticket? =
        branches.graph.parent(ticket)?.takeIf { p -> p.body.checklist("Plan").any { it.childId == ticket.fileId && !it.checked } }

    /**
     * Ids of the tickets merged elsewhere (a pull request, a `git merge` by hand) and not set
     * `done`: in `review` on their target. A ticket in review is on its target only once its
     * branch is merged there, so these are done.
     */
    public fun merged(branches: Branches): List<String> = branches.tickets
        .filter { it.frontmatter?.status == Status.REVIEW && !it.onlyOnBranch && it.branch == branches.targetBranch(it.fileId) }
        .map { it.fileId }

    /**
     * Sets the status on the ticket's real copy and, for `done`, checks it in its parent's Plan.
     * Commits only those files: in the worktree that has the branch checked out, else on the
     * branch without checking it out. One commit per branch (the parent usually shares the
     * child's, as children merge into it), each reported to [committed] as it is made.
     */
    public fun perform(repository: Repository, ready: FinishResult.Ready, today: String, committed: (FinishCommit) -> Unit = {}) {
        val id = ready.id
        val edits = linkedMapOf<String, MutableList<TicketChange>>()
        edits.getOrPut(ready.branch) { mutableListOf() } += TicketChange(id) { text, _ -> edit(text, ready.status, today) }
        val parent = ready.parent
        val parentBranch = ready.parentBranch
        if (parent != null && parentBranch != null) {
            edits.getOrPut(parentBranch) { mutableListOf() } += TicketChange(parent.fileId) { text, path -> checkInParent(text, path, id, today) }
        }
        for ((branch, changes) in edits) committed(commit(repository, branch, changes, "$id: ${ready.status.text}"))
    }

    internal fun commit(repository: Repository, branch: String, changes: List<TicketChange>, message: String): FinishCommit {
        val view = BranchView.open(repository, branch) ?: throw FinishException("can't read the branch '$branch'.")
        val files = changes.map { c ->
            val ticket = view.repository.ticket(c.ticketId) ?: throw FinishException("no ticket '${c.ticketId}' on '$branch'.")
            val text = try { c.change(ticket.text, ticket.path) } catch (e: TicketEditException) {
                throw FinishException(e.message ?: e.toString())
            }
            PlannedFile(ticket.path, text, isNew = false)
        }
        // A reference to a ticket created on another branch meanwhile (new --on main) is fine, as in validate.
        val problems = Validator(view.withFiles(files), otherIds = Branches.ids(repository)).validate(files.map { it.path })
        if (problems.isNotEmpty()) throw FinishException("the changed ticket wouldn't be valid", problems)
        return FinishCommit(branch, view.commit(files, message), message)
    }

    /** [text] with [status], `updated` and the Work Log entry. */
    public fun edit(text: String, status: Status, today: String): String =
        TicketEditor(text)
            .setField("status", status.text)
            .setField("updated", today)
            .appendWorkLog(today, "status", status.text)
            .text

    /** The parent's [text] with the Plan item for child [id] checked (SPEC §7.5). */
    public fun checkInParent(text: String, parentPath: okio.Path, id: String, today: String): String {
        val item = Ticket(parentPath, text).body.checklist("Plan").firstOrNull { it.childId == id && !it.checked } ?: return text
        return TicketEditor(text).setChecked(item.line, true).setField("updated", today).text
    }
}

public sealed interface ReopenResult {
    public data class Ready(val id: String, val branch: String, val reason: String) : ReopenResult
    public data class Refused(val reason: String) : ReopenResult
}

/**
 * Back from `review` to `in-progress` when the review finds something to change, with the reason
 * logged (SPEC §6.1). The ticket stays on its branch and in its worktree.
 */
public object Reopen {
    public fun prepare(branches: Branches, id: String, reason: String): ReopenResult {
        if (!isValidId(id)) return ReopenResult.Refused("'$id' is not a ticket id (SPEC §3)")
        val ticket = branches.graph.ticket(id) ?: return ReopenResult.Refused("no ticket '$id' on any branch")
        val status = ticket.frontmatter?.status
        if (status != Status.REVIEW) {
            return ReopenResult.Refused("'$id' is ${status?.text ?: "without a readable status"}; only a ticket in review goes back to in-progress")
        }
        val branch = ticket.branch ?: return ReopenResult.Refused("can't tell which branch has '$id'")
        if (branch != id) {
            return ReopenResult.Refused("'$id' is merged into $branch already, so there is no branch to go back to: what is left is a new ticket")
        }
        val why = reason.lines().joinToString(" ") { it.trim() }.trim()
        if (why.isEmpty()) return ReopenResult.Refused("going back needs the reason, for the Work Log (SPEC §6.1)")
        return ReopenResult.Ready(id, branch, why)
    }

    /** Sets `in-progress` and logs `status · reopened: <reason>`, committing only the ticket on its branch. */
    public fun perform(repository: Repository, ready: ReopenResult.Ready, today: String): FinishCommit =
        Finish.commit(repository, ready.branch, listOf(TicketChange(ready.id) { text, _ -> edit(text, ready.reason, today) }), "${ready.id}: reopen")

    public fun edit(text: String, reason: String, today: String): String =
        TicketEditor(text)
            .setField("status", Status.IN_PROGRESS.text)
            .setField("updated", today)
            .appendWorkLog(today, "status", "reopened: $reason")
            .text
}
