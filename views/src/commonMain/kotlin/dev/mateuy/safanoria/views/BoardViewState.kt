package dev.mateuy.safanoria.views

import dev.mateuy.safanoria.core.Board
import dev.mateuy.safanoria.core.BoardGroup
import dev.mateuy.safanoria.core.Config
import dev.mateuy.safanoria.core.Diagnostic
import dev.mateuy.safanoria.core.Priority
import dev.mateuy.safanoria.core.Progress
import dev.mateuy.safanoria.core.Severity
import dev.mateuy.safanoria.core.Size
import dev.mateuy.safanoria.core.Ticket
import dev.mateuy.safanoria.core.TicketFilter
import dev.mateuy.safanoria.core.TicketGraph
import dev.mateuy.safanoria.core.TicketType

/** What the board screen shows. */
data class BoardViewState(
    val columns: List<BoardColumn> = emptyList(),
    /** Tickets in the project, and how many of them are cards: those that pass [filter], children only with [showChildren]. */
    val ticketCount: Int = 0,
    val shownCount: Int = 0,
    val filter: TicketFilter = TicketFilter(),
    val showChildren: Boolean = false,
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
    /** The remote-tracking branch (`origin/<id>`) when the ticket is being worked on another computer. */
    val remoteBranch: String? = null,
    /** The versions it was released in (`resolvedIn`), e.g. `app 4.3.0`. */
    val versions: String? = null,
    /** `validate` problems in the ticket. */
    val errors: Int = 0,
    val warnings: Int = 0,
)

/**
 * Columns left to right, the way a ticket moves. Not core's `TicketGraph.STATUS_ORDER`, which
 * puts work in hand first for lists read from the top.
 */
val COLUMN_ORDER: List<BoardGroup> = listOf(
    BoardGroup.BACKLOG, BoardGroup.READY, BoardGroup.IN_PROGRESS, BoardGroup.REVIEW,
    BoardGroup.TO_RELEASE, BoardGroup.RELEASED, BoardGroup.WONTFIX,
)

/**
 * Every ticket that passes [filter] as a card in the column of its group: its status, with `done`
 * in two columns, to release and released. Every group has a column, even when empty. Within a
 * column, cards keep the graph's order (priority, then id). Children are cards only with
 * [showChildren]; one whose parent isn't in the project always is, as nothing else leads to it.
 */
fun boardViewState(
    snapshot: TicketsSnapshot,
    collapsed: Set<BoardGroup?> = emptySet(),
    filter: TicketFilter = TicketFilter(),
    showChildren: Boolean = false,
): BoardViewState {
    val graph = snapshot.graph
    val config = snapshot.config
    if (graph == null || config == null) {
        return BoardViewState(loading = snapshot.loading || snapshot.error == null, error = snapshot.error, filter = filter, showChildren = showChildren)
    }
    val shown = graph.tickets.filter { (showChildren || graph.parent(it) == null) && filter.matches(graph, it) }
    val columns = columns(snapshot, graph, config, shown) { group, _ -> group in collapsed }
    return BoardViewState(
        columns = columns,
        ticketCount = graph.tickets.size,
        shownCount = shown.size,
        filter = filter,
        showChildren = showChildren,
        areas = graph.tickets.flatMap { t -> t.frontmatter?.area.orEmpty().map { it.value } }.distinct().sorted(),
        tags = graph.tickets.flatMap { t -> t.frontmatter?.tags.orEmpty().map { it.value } }.distinct().sorted(),
        projectProblems = snapshot.projectDiagnostics.map { it.toString() },
        loading = snapshot.loading,
        error = snapshot.error,
    )
}

/**
 * The children of [parent] as a board, for its screen: the columns of the board, with the children
 * in the parent's Plan order. Empty when it has none. A column without children is collapsed, so
 * that the ones with children get the width.
 */
fun childrenColumns(snapshot: TicketsSnapshot, parent: Ticket): List<BoardColumn> {
    val graph = snapshot.graph ?: return emptyList()
    val config = snapshot.config ?: return emptyList()
    val children = graph.children(parent).ifEmpty { return emptyList() }
    // Every card here has the same parent, the ticket on screen: not said on each.
    return columns(snapshot, graph, config, children, withParent = false) { _, cards -> cards.isEmpty() }
}

/** [tickets] as cards in the column of their group, in the order given. [collapsed] says which columns are. */
private fun columns(
    snapshot: TicketsSnapshot,
    graph: TicketGraph,
    config: Config,
    tickets: List<Ticket>,
    withParent: Boolean = true,
    collapsed: (BoardGroup?, List<TicketCard>) -> Boolean,
): List<BoardColumn> {
    val byGroup = tickets.groupBy { BoardGroup.of(it, config) }
    // The column for unreadable statuses only exists when there are such tickets.
    return (COLUMN_ORDER + listOf(null))
        .filter { it != null || null in byGroup }
        .map { group ->
            val cards = byGroup[group].orEmpty().map { card(graph, it, snapshot.diagnostics[it.fileId].orEmpty(), withParent) }
            BoardColumn(group, cards, collapsed(group, cards))
        }
}

private fun card(graph: TicketGraph, ticket: Ticket, diagnostics: List<Diagnostic>, withParent: Boolean): TicketCard {
    val f = ticket.frontmatter
    return TicketCard(
        id = ticket.fileId,
        title = f?.title?.value ?: "(no title)",
        type = f?.type,
        priority = f?.priority,
        size = f?.size,
        parentId = f?.parent?.value?.takeIf { withParent },
        tags = f?.tags.orEmpty().map { it.value },
        progress = graph.progress(ticket)?.takeIf { graph.children(ticket).isNotEmpty() },
        openBlockers = graph.openBlockers(ticket),
        onlyOnBranch = ticket.branch?.takeIf { ticket.onlyOnBranch },
        remoteBranch = ticket.remoteBranch,
        versions = Board.versions(ticket),
        errors = diagnostics.count { it.severity == Severity.ERROR },
        warnings = diagnostics.count { it.severity == Severity.WARNING },
    )
}
