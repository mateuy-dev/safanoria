package dev.mateuy.safanoria.gui.board

import dev.mateuy.safanoria.core.BoardGroup
import dev.mateuy.safanoria.core.Priority
import dev.mateuy.safanoria.core.Progress
import dev.mateuy.safanoria.core.Size
import dev.mateuy.safanoria.core.TicketFilter
import dev.mateuy.safanoria.core.TicketType

/** What the board screen shows. */
data class BoardViewState(
    val columns: List<BoardColumn> = emptyList(),
    /** Tickets in the project, and how many of them pass [filter]. */
    val ticketCount: Int = 0,
    val shownCount: Int = 0,
    val filter: TicketFilter = TicketFilter(),
    /** The areas tickets have, to filter by. */
    val areas: List<String> = emptyList(),
    /** The tags tickets have, to filter by. */
    val tags: List<String> = emptyList(),
    /** Problems outside tickets (`safanoria.yaml`, attachments), as `validate` prints them. */
    val projectProblems: List<String> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * A group and its tickets, in board order. [group] is null for tickets whose status can't be
 * read. A [collapsed] column shows only its name and count.
 */
data class BoardColumn(val group: BoardGroup?, val cards: List<TicketCard>, val collapsed: Boolean = false)

data class TicketCard(
    val id: String,
    val title: String,
    val type: TicketType?,
    val priority: Priority?,
    val size: Size?,
    val parentId: String?,
    val tags: List<String> = emptyList(),
    /** Checked Plan items out of all; null when the Plan has no items. */
    val progress: Progress?,
    /** `blockedBy` ids that are not done. */
    val openBlockers: List<String>,
    /** The branch, when the ticket exists only there. */
    val onlyOnBranch: String?,
    /** The versions it was released in (`resolvedIn`), e.g. `app 4.3.0`. */
    val versions: String? = null,
    /** `validate` problems in the ticket. */
    val errors: Int = 0,
    val warnings: Int = 0,
)
