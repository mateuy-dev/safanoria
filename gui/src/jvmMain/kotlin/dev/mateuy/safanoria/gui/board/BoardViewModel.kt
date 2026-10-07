package dev.mateuy.safanoria.gui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mateuy.safanoria.core.Board
import dev.mateuy.safanoria.core.BoardGroup
import dev.mateuy.safanoria.core.Diagnostic
import dev.mateuy.safanoria.core.Severity
import dev.mateuy.safanoria.core.Ticket
import dev.mateuy.safanoria.core.TicketFilter
import dev.mateuy.safanoria.core.TicketGraph
import dev.mateuy.safanoria.core.TicketType
import dev.mateuy.safanoria.gui.data.TicketStore
import dev.mateuy.safanoria.gui.data.TicketsSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BoardViewModel(private val store: TicketStore) : ViewModel() {
    /**
     * Closed work is collapsed until asked for: it is most of the tickets and rarely what is looked
     * for. Not what is to release: that is what the next version brings.
     */
    private val collapsed = MutableStateFlow<Set<BoardGroup?>>(setOf(BoardGroup.RELEASED, BoardGroup.WONTFIX))

    private val filter = MutableStateFlow(TicketFilter())

    val state: StateFlow<BoardViewState> = combine(store.snapshot, collapsed, filter, ::boardViewState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoardViewState())

    /** Collapses the column of [group], or expands it when collapsed. */
    fun toggleColumn(group: BoardGroup?) {
        collapsed.update { if (group in it) it - group else it + group }
    }

    fun toggleType(type: TicketType) {
        filter.update { it.copy(types = it.types.toggled(type)) }
    }

    fun toggleArea(area: String) {
        filter.update { it.copy(areas = it.areas.toggled(area)) }
    }

    fun toggleTag(tag: String) {
        filter.update { it.copy(tags = it.tags.toggled(tag)) }
    }

    /** Only tickets with a `blockedBy` that isn't done. */
    fun toggleBlocked() {
        filter.update { it.copy(blocked = !it.blocked) }
    }

    fun clearFilter() {
        filter.value = TicketFilter()
    }

    private fun <T> Set<T>.toggled(value: T) = if (value in this) this - value else this + value

    fun refresh() {
        viewModelScope.launch { store.refresh() }
    }
}

/**
 * Columns left to right, the way a ticket moves. Not core's `TicketGraph.STATUS_ORDER`, which
 * puts work in hand first for lists read from the top.
 */
internal val COLUMN_ORDER: List<BoardGroup> = listOf(
    BoardGroup.BACKLOG, BoardGroup.READY, BoardGroup.IN_PROGRESS, BoardGroup.REVIEW,
    BoardGroup.TO_RELEASE, BoardGroup.RELEASED, BoardGroup.WONTFIX,
)

/**
 * Every ticket that passes [filter] as a card in the column of its group: its status, with `done`
 * in two columns, to release and released. Every group has a column, even when empty. Within a
 * column, cards keep the graph's order (priority, then id).
 */
internal fun boardViewState(
    snapshot: TicketsSnapshot,
    collapsed: Set<BoardGroup?> = emptySet(),
    filter: TicketFilter = TicketFilter(),
): BoardViewState {
    val graph = snapshot.graph
    val config = snapshot.config
    if (graph == null || config == null) return BoardViewState(loading = snapshot.loading || snapshot.error == null, error = snapshot.error, filter = filter)
    val shown = graph.tickets.filter { filter.matches(graph, it) }
    val byGroup = shown.groupBy { BoardGroup.of(it, config) }
    fun card(ticket: Ticket) = card(graph, ticket, snapshot.diagnostics[ticket.fileId].orEmpty())
    // The column for unreadable statuses only exists when there are such tickets.
    val columns = (COLUMN_ORDER + listOf(null))
        .filter { it != null || null in byGroup }
        .map { group -> BoardColumn(group, byGroup[group].orEmpty().map(::card), group in collapsed) }
    return BoardViewState(
        columns = columns,
        ticketCount = graph.tickets.size,
        shownCount = shown.size,
        filter = filter,
        areas = graph.tickets.flatMap { t -> t.frontmatter?.area.orEmpty().map { it.value } }.distinct().sorted(),
        tags = graph.tickets.flatMap { t -> t.frontmatter?.tags.orEmpty().map { it.value } }.distinct().sorted(),
        projectProblems = snapshot.projectDiagnostics.map { it.toString() },
        loading = snapshot.loading,
        error = snapshot.error,
    )
}

private fun card(graph: TicketGraph, ticket: Ticket, diagnostics: List<Diagnostic>): TicketCard {
    val f = ticket.frontmatter
    return TicketCard(
        id = ticket.fileId,
        title = f?.title?.value ?: "(no title)",
        type = f?.type,
        priority = f?.priority,
        size = f?.size,
        parentId = f?.parent?.value,
        tags = f?.tags.orEmpty().map { it.value },
        progress = graph.progress(ticket)?.takeIf { graph.children(ticket).isNotEmpty() },
        openBlockers = graph.openBlockers(ticket),
        onlyOnBranch = ticket.branch?.takeIf { ticket.onlyOnBranch },
        versions = Board.versions(ticket),
        errors = diagnostics.count { it.severity == Severity.ERROR },
        warnings = diagnostics.count { it.severity == Severity.WARNING },
    )
}
