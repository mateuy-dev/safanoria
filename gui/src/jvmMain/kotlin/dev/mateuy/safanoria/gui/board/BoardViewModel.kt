package dev.mateuy.safanoria.gui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mateuy.safanoria.core.Status
import dev.mateuy.safanoria.core.Ticket
import dev.mateuy.safanoria.core.TicketGraph
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
    /** Closed work is collapsed until asked for: it is most of the tickets and rarely what is looked for. */
    private val collapsed = MutableStateFlow<Set<Status?>>(setOf(Status.DONE, Status.WONTFIX))

    val state: StateFlow<BoardViewState> = combine(store.snapshot, collapsed, ::boardViewState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoardViewState())

    /** Collapses the column of [status], or expands it when collapsed. */
    fun toggleColumn(status: Status?) {
        collapsed.update { if (status in it) it - status else it + status }
    }

    fun refresh() {
        viewModelScope.launch { store.refresh() }
    }
}

/** Every ticket as a card in its status column; every status has a column, even when empty. */
internal fun boardViewState(snapshot: TicketsSnapshot, collapsed: Set<Status?> = emptySet()): BoardViewState {
    val graph = snapshot.graph ?: return BoardViewState(loading = snapshot.loading || snapshot.error == null, error = snapshot.error)
    val byStatus = graph.tickets.groupBy { it.frontmatter?.status }
    // The column for unreadable statuses only exists when there are such tickets.
    val columns = (TicketGraph.STATUS_ORDER + listOf(null))
        .filter { it != null || null in byStatus }
        .map { status -> BoardColumn(status, byStatus[status].orEmpty().map { card(graph, it) }, status in collapsed) }
    return BoardViewState(columns, graph.tickets.size, snapshot.loading, snapshot.error)
}

private fun card(graph: TicketGraph, ticket: Ticket): TicketCard {
    val f = ticket.frontmatter
    return TicketCard(
        id = ticket.fileId,
        title = f?.title?.value ?: "(no title)",
        type = f?.type,
        priority = f?.priority,
        size = f?.size,
        parentId = f?.parent?.value,
        progress = graph.progress(ticket)?.takeIf { graph.children(ticket).isNotEmpty() },
        openBlockers = graph.openBlockers(ticket),
        onlyOnBranch = ticket.branch?.takeIf { ticket.onlyOnBranch },
    )
}
