package dev.mateuy.safanoria.gui.ticket

import dev.mateuy.safanoria.core.Status

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
    val blockedBy: List<TicketLink> = emptyList(),
    val blocks: List<TicketLink> = emptyList(),
    /** The markdown after the frontmatter. */
    val body: String = "",
    /** `validate` problems in the ticket, in file order. */
    val problems: List<TicketProblem> = emptyList(),
    /** What can be done with the ticket in its status, or null. */
    val action: TicketAction? = null,
    /** True while [action] runs. */
    val busy: Boolean = false,
    /** How the last action went, when there is something to tell. */
    val notice: Notice? = null,
)

enum class TicketAction {
    /** Backlog or ready: start it (branch, worktree) and open a terminal there. */
    START,

    /** In progress or review: open a terminal where its branch is checked out. */
    OPEN_TERMINAL,
}

data class Notice(val text: String, val error: Boolean)

/** A `validate` problem: its [code] is stable (SPEC §12), [line] is in the ticket file. */
data class TicketProblem(val line: Int?, val code: String, val message: String, val error: Boolean)

/** Another ticket, to navigate to. [status] is null when the ticket is missing or unreadable. */
data class TicketLink(val id: String, val title: String?, val status: Status?)
