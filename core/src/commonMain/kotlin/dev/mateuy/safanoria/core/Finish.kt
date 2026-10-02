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

/**
 * Finish (SPEC §11.4): `review` when the work is complete, `done` once merged into its target.
 * [prepare] only checks and decides where each copy is; the caller applies [edit] and
 * [checkInParent] there and commits.
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
