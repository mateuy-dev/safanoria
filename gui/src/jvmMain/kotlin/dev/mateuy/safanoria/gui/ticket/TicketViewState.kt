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
)

/** A `validate` problem: its [code] is stable (SPEC §12), [line] is in the ticket file. */
data class TicketProblem(val line: Int?, val code: String, val message: String, val error: Boolean)

/** Another ticket, to navigate to. [status] is null when the ticket is missing or unreadable. */
data class TicketLink(val id: String, val title: String?, val status: Status?)
