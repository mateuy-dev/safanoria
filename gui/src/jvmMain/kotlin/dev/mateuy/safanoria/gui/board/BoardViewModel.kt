package dev.mateuy.safanoria.gui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mateuy.safanoria.core.Ticket
import dev.mateuy.safanoria.core.TicketGraph
import dev.mateuy.safanoria.gui.data.TicketStore
import dev.mateuy.safanoria.gui.data.TicketsSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BoardViewModel(private val store: TicketStore) : ViewModel() {
    val state: StateFlow<BoardViewState> = store.snapshot
        .map(::boardViewState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoardViewState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { store.refresh() }
    }
}

/** Every ticket as a card in its status column; every status has a column, even when empty. */
internal fun boardViewState(snapshot: TicketsSnapshot): BoardViewState {
    val graph = snapshot.graph ?: return BoardViewState(loading = snapshot.loading || snapshot.error == null, error = snapshot.error)
    val byStatus = graph.tickets.groupBy { it.frontmatter?.status }
    val columns = TicketGraph.STATUS_ORDER.map { status -> BoardColumn(status, byStatus[status].orEmpty().map { card(graph, it) }) } +
        listOfNotNull(byStatus[null]?.let { unreadable -> BoardColumn(null, unreadable.map { card(graph, it) }) })
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
