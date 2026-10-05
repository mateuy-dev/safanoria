package dev.mateuy.safanoria.gui.ticket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mateuy.safanoria.core.Ticket
import dev.mateuy.safanoria.core.TicketGraph
import dev.mateuy.safanoria.core.text
import dev.mateuy.safanoria.gui.data.TicketStore
import dev.mateuy.safanoria.gui.data.TicketsSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class TicketViewModel(private val id: String, store: TicketStore) : ViewModel() {
    val state: StateFlow<TicketViewState> = store.snapshot
        .map { ticketViewState(id, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ticketViewState(id, store.snapshot.value))
}

internal fun ticketViewState(id: String, snapshot: TicketsSnapshot): TicketViewState {
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
            f?.assignee?.let { "assignee" to it.value },
            f?.created?.let { "created" to it.value },
            f?.updated?.let { "updated" to it.value },
            ticket.branch?.let { "read from" to it },
        ),
        parent = f?.parent?.value?.let(::link),
        children = graph.children(ticket).map(::link),
        blockedBy = f?.blockedBy.orEmpty().map { it.value }.distinct().map(::link),
        blocks = graph.blocks(ticket).map(::link),
        body = body(ticket),
    )
}

/** The file's text after the frontmatter. */
private fun body(ticket: Ticket): String {
    val lines = ticket.text.split('\n')
    val close = lines.drop(1).indexOfFirst { it.trimEnd() == "---" }
    val start = if (lines.firstOrNull()?.trimEnd() == "---" && close >= 0) close + 2 else 0
    return lines.drop(start).joinToString("\n").trim()
}
