package dev.mateuy.safanoria.gui.versions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mateuy.safanoria.core.Release
import dev.mateuy.safanoria.core.Ticket
import dev.mateuy.safanoria.core.TicketGraph
import dev.mateuy.safanoria.core.Version
import dev.mateuy.safanoria.gui.data.TicketStore
import dev.mateuy.safanoria.gui.data.TicketsSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VersionsViewModel(private val store: TicketStore) : ViewModel() {
    /** The component chosen; null until one is, which shows the project's first. */
    private val component = MutableStateFlow<String?>(null)

    val state: StateFlow<VersionsViewState> = combine(store.snapshot, component, ::versionsViewState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VersionsViewState())

    fun selectComponent(name: String) {
        component.value = name
    }

    fun refresh() {
        viewModelScope.launch { store.refresh() }
    }
}

/**
 * The tickets of [component] (the project's first when null or unknown) grouped by the version
 * that shipped them: its `done` tickets still to release first, then its versions, newest first,
 * ordered as versions. Within a group tickets are in id order, as `safanoria-cli notes` lists
 * them, with children under their parent when both are in the group.
 */
internal fun versionsViewState(snapshot: TicketsSnapshot, component: String? = null): VersionsViewState {
    val graph = snapshot.graph
    val config = snapshot.config
    if (graph == null || config == null) return VersionsViewState(loading = snapshot.loading || snapshot.error == null, error = snapshot.error)
    val components = config.components.keys.toList()
    val shown = component?.takeIf { it in components } ?: components.firstOrNull()
        ?: return VersionsViewState(loading = snapshot.loading, error = snapshot.error)
    val unreleased = graph.tickets.filter { shown in Release.pending(it, config) }
    val byVersion = graph.tickets
        .mapNotNull { t -> t.frontmatter?.resolvedIn?.get(shown)?.value?.let(Version::parse)?.let { it to t } }
        .groupBy({ it.first }, { it.second })
    val releases = listOfNotNull(unreleased.takeIf { it.isNotEmpty() }?.let { group(graph, null, it) }) +
        byVersion.entries.sortedByDescending { it.key }.map { (version, tickets) -> group(graph, version, tickets) }
    return VersionsViewState(components, shown, releases, snapshot.loading, snapshot.error)
}

private fun group(graph: TicketGraph, version: Version?, tickets: List<Ticket>): VersionGroup {
    val here = tickets.map { it.fileId }.toSet()
    fun row(ticket: Ticket, nested: Boolean): VersionTicket {
        val f = ticket.frontmatter
        return VersionTicket(
            id = ticket.fileId,
            title = f?.title?.value ?: "(no title)",
            type = f?.type,
            parentId = f?.parent?.value?.takeIf { !nested },
            children = graph.children(ticket).filter { it.fileId in here }.map { row(it, nested = true) },
        )
    }
    // A child whose parent is elsewhere (an earlier version of its own, or a missing parent) is a row here.
    val top = tickets.filter { graph.parent(it)?.fileId !in here }.sortedBy { it.fileId }
    return VersionGroup(version?.toString(), top.map { row(it, nested = false) })
}
