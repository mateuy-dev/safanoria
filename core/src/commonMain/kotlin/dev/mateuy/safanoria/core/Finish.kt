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
    ) : FinishResult

    public data class Refused(val reason: String) : FinishResult
}

/** Why [Finish.perform] didn't finish the ticket; [problems] when the changed tickets wouldn't validate. */
public class FinishException(message: String, public val problems: List<Diagnostic> = emptyList()) : Exception(message)

/** A commit [Finish.perform] made. */
public data class FinishCommit(val branch: String, val commit: String, val message: String)

/**
 * Finish (SPEC §11.4): `review` when the work is complete, `done` once merged into its target.
 * [prepare] only checks and decides where each copy is; [perform] applies [edit] and
 * [checkInParent] there and commits. The CLI and apps share these.
 */
public object Finish {
    public fun prepare(branches: Branches, id: String, status: Status): FinishResult {
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
        val parent = if (status == Status.DONE) branches.graph.parent(ticket)?.takeIf { p -> p.body.checklist("Plan").any { it.childId == id && !it.checked } } else null
        return FinishResult.Ready(id, status, branch, parent, parent?.branch)
    }

    /**
     * Sets the status on the ticket's real copy and, for `done`, checks it in its parent's Plan.
     * Commits only those files: in the worktree that has the branch checked out, else on the
     * branch without checking it out. One commit per branch (the parent usually shares the
     * child's, as children merge into it), each reported to [committed] as it is made.
     */
    public fun perform(repository: Repository, ready: FinishResult.Ready, today: String, committed: (FinishCommit) -> Unit = {}) {
        val id = ready.id
        val edits = linkedMapOf<String, MutableList<Pair<String, (String, okio.Path) -> String>>>()
        edits.getOrPut(ready.branch) { mutableListOf() } += id to { text, _ -> edit(text, ready.status, today) }
        val parent = ready.parent
        val parentBranch = ready.parentBranch
        if (parent != null && parentBranch != null) {
            edits.getOrPut(parentBranch) { mutableListOf() } += parent.fileId to { text, path -> checkInParent(text, path, id, today) }
        }
        for ((branch, changes) in edits) committed(commit(repository, branch, changes, "$id: ${ready.status.text}"))
    }

    private fun commit(repository: Repository, branch: String, changes: List<Pair<String, (String, okio.Path) -> String>>, message: String): FinishCommit {
        val view = BranchView.open(repository, branch) ?: throw FinishException("can't read the branch '$branch'.")
        val files = changes.map { (ticketId, change) ->
            val ticket = view.repository.ticket(ticketId) ?: throw FinishException("no ticket '$ticketId' on '$branch'.")
            val text = try { change(ticket.text, ticket.path) } catch (e: TicketEditException) {
                throw FinishException(e.message ?: e.toString())
            }
            PlannedFile(ticket.path, text, isNew = false)
        }
        val problems = Validator(view.withFiles(files)).validate(files.map { it.path })
        if (problems.isNotEmpty()) throw FinishException("the finished ticket wouldn't be valid", problems)
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
