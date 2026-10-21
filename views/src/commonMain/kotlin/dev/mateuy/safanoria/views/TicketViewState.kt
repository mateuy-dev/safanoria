package dev.mateuy.safanoria.views

import dev.mateuy.safanoria.core.Severity
import dev.mateuy.safanoria.core.Status
import dev.mateuy.safanoria.core.Ticket
import dev.mateuy.safanoria.core.text

/** What the ticket screen shows. */
data class TicketViewState(
    val id: String,
    val loading: Boolean = true,
    /** False when no ticket has this id (any more). */
    val found: Boolean = true,
    val title: String = "",
    val status: Status? = null,
    /** Frontmatter facts as label and value: type, priority, size, dates, branch. */
    val facts: List<Pair<String, String>> = emptyList(),
    val parent: TicketLink? = null,
    val children: List<TicketLink> = emptyList(),
    /** The children as a board, a column per status; empty when the ticket has none. */
    val childrenBoard: List<BoardColumn> = emptyList(),
    val blockedBy: List<TicketLink> = emptyList(),
    val blocks: List<TicketLink> = emptyList(),
    /** The markdown after the frontmatter. */
    val body: String = "",
    /** `validate` problems in the ticket, in file order. */
    val problems: List<TicketProblem> = emptyList(),
    /** What can be done with the ticket in its status. */
    val actions: List<TicketAction> = emptyList(),
    /** The action waiting for the user to confirm it, or null. */
    val confirming: TicketAction? = null,
    /** True while one of the [actions] runs. */
    val busy: Boolean = false,
    /** How the last action went, when there is something to tell. */
    val notice: Notice? = null,
)

/** [confirmed] actions change the repository (a branch, a commit), so the user is asked first. */
enum class TicketAction(val confirmed: Boolean) {
    /** Backlog or ready: start it (branch, worktree) and open a terminal there. */
    START(confirmed = true),

    /** Backlog or ready: start it like [START], with Claude Code running in the terminal, told to work on the ticket. */
    START_IN_CLAUDE(confirmed = true),

    /** Started on another computer (only on `origin/<id>`): add its worktree here, with a local branch tracking it, and open a terminal there. */
    CHECK_OUT(confirmed = true),

    /** In progress or review: open a terminal where its branch is checked out. */
    OPEN_TERMINAL(confirmed = false),

    /** In progress: the work is complete, set it to review. */
    FINISH(confirmed = true),

    /** In review: merge its branch into its target with the ticket done, and remove its worktree and branch. */
    MERGE(confirmed = true),

    /** In review: back to in progress. Confirming it takes the reason, which is logged. */
    REOPEN(confirmed = true),
}

data class Notice(val text: String, val error: Boolean)

/** How finishing went, as told to the user. */
val FinishOutcome.notice: Notice
    get() = when (this) {
        is FinishOutcome.NotFinished -> Notice("Not finished: $reason", error = true)
        is FinishOutcome.Finished -> Notice((listOf("In review, committed on $branch") + open.map { "Still open: $it" }).joinToString("\n"), error = false)
    }

/** How merging went, as told to the user. */
val MergeOutcome.notice: Notice
    get() = when (this) {
        is MergeOutcome.NotMerged -> Notice("Not merged: $reason", error = true)
        is MergeOutcome.Merged -> Notice((listOf("Merged into $target and done; nothing was pushed") + cleanUp).joinToString("\n"), error = false)
    }

/** How reopening went, as told to the user. */
val ReopenOutcome.notice: Notice
    get() = when (this) {
        is ReopenOutcome.NotReopened -> Notice("Not reopened: $reason", error = true)
        is ReopenOutcome.Reopened -> Notice("In progress again, committed on $branch", error = false)
    }

/** A `validate` problem: its [code] is stable (SPEC §12), [line] is in the ticket file. */
data class TicketProblem(val line: Int?, val code: String, val message: String, val error: Boolean)

/** Another ticket, to navigate to. [status] is null when the ticket is missing or unreadable. */
data class TicketLink(val id: String, val title: String?, val status: Status?)

fun ticketViewState(id: String, snapshot: TicketsSnapshot): TicketViewState {
    val graph = snapshot.graph ?: return TicketViewState(id, loading = true)
    val ticket = graph.ticket(id) ?: return TicketViewState(id, loading = false, found = false)
    val f = ticket.frontmatter
    fun link(t: Ticket) = TicketLink(t.fileId, t.frontmatter?.title?.value, t.frontmatter?.status)
    fun link(otherId: String) = graph.ticket(otherId)?.let(::link) ?: TicketLink(otherId, null, null)
    return TicketViewState(
        id = id,
        loading = false,
        title = f?.title?.value ?: "(no title)",
        status = f?.status,
        facts = listOfNotNull(
            f?.type?.let { "type" to it.text },
            f?.priority?.let { "priority" to it.text },
            f?.size?.let { "size" to it.text },
            f?.area?.takeIf { it.isNotEmpty() }?.let { areas -> "area" to areas.joinToString(", ") { it.value } },
            f?.tags?.takeIf { it.isNotEmpty() }?.let { tags -> "tags" to tags.joinToString(", ") { it.value } },
            f?.assignee?.let { "assignee" to it.value },
            f?.created?.let { "created" to it.value },
            f?.updated?.let { "updated" to it.value },
            (ticket.remoteBranch ?: ticket.branch)?.let { "read from" to it },
        ),
        parent = f?.parent?.value?.let(::link),
        children = graph.children(ticket).map(::link),
        childrenBoard = childrenColumns(snapshot, ticket),
        blockedBy = f?.blockedBy.orEmpty().map { it.value }.distinct().map(::link),
        blocks = graph.blocks(ticket).map(::link),
        body = body(ticket),
        // Started on another computer: nothing can be done with it here until it is checked out.
        actions = if (ticket.remote != null) listOf(TicketAction.CHECK_OUT) else when (f?.status) {
            Status.BACKLOG, Status.READY -> listOf(TicketAction.START, TicketAction.START_IN_CLAUDE)
            Status.IN_PROGRESS -> listOf(TicketAction.OPEN_TERMINAL, TicketAction.FINISH)
            Status.REVIEW -> listOf(TicketAction.OPEN_TERMINAL, TicketAction.MERGE, TicketAction.REOPEN)
            else -> emptyList()
        },
        problems = snapshot.diagnostics[id].orEmpty().map { TicketProblem(it.line, it.code, it.message, it.severity == Severity.ERROR) },
    )
}

/** The file's text after the frontmatter. */
private fun body(ticket: Ticket): String {
    val lines = ticket.text.split('\n')
    val close = lines.drop(1).indexOfFirst { it.trimEnd() == "---" }
    val start = if (lines.firstOrNull()?.trimEnd() == "---" && close >= 0) close + 2 else 0
    return lines.drop(start).joinToString("\n").trim()
}
