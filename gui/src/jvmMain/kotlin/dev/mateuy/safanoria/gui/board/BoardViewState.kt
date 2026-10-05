package dev.mateuy.safanoria.gui.board

import dev.mateuy.safanoria.core.Priority
import dev.mateuy.safanoria.core.Progress
import dev.mateuy.safanoria.core.Size
import dev.mateuy.safanoria.core.Status
import dev.mateuy.safanoria.core.TicketType

/** What the board screen shows. */
data class BoardViewState(
    val columns: List<BoardColumn> = emptyList(),
    val ticketCount: Int = 0,
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * A status and its tickets, in board order. [status] is null for tickets whose status can't be
 * read. A [collapsed] column shows only its name and count.
 */
data class BoardColumn(val status: Status?, val cards: List<TicketCard>, val collapsed: Boolean = false)

data class TicketCard(
    val id: String,
    val title: String,
    val type: TicketType?,
    val priority: Priority?,
    val size: Size?,
    val parentId: String?,
    /** Checked Plan items out of all; null when the Plan has no items. */
    val progress: Progress?,
    /** `blockedBy` ids that are not done. */
    val openBlockers: List<String>,
    /** The branch, when the ticket exists only there. */
    val onlyOnBranch: String?,
)
