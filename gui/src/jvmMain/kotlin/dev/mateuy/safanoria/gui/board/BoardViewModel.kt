package dev.mateuy.safanoria.gui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mateuy.safanoria.core.Board
import dev.mateuy.safanoria.core.BoardGroup
import dev.mateuy.safanoria.core.Config
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

    /** Children are hidden until asked for: their parent's card shows their progress, its screen their board. */
    private val showChildren = MutableStateFlow(false)

    val state: StateFlow<BoardViewState> = combine(store.snapshot, collapsed, filter, showChildren, ::boardViewState)
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

    fun toggleChildren() {
        showChildren.update { !it }
    }

    fun clearFilter() {
        filter.value = TicketFilter()
    }

    private fun <T> Set<T>.toggled(value: T) = if (value in this) this - value else this + value

    fun refresh() {
        viewModelScope.launch { store.refresh() }
    }

    /** Fetches the remotes, then reads the tickets again. */
    fun fetch() {
        viewModelScope.launch { store.fetch() }
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
 * column, cards keep the graph's order (priority, then id). Children are cards only with
 * [showChildren]; one whose parent isn't in the project always is, as nothing else leads to it.
 */
internal fun boardViewState(
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
internal fun childrenColumns(snapshot: TicketsSnapshot, parent: Ticket): List<BoardColumn> {
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
