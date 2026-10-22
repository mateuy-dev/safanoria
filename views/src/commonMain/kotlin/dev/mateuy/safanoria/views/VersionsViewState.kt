package dev.mateuy.safanoria.views

import dev.mateuy.safanoria.core.Release
import dev.mateuy.safanoria.core.Ticket
import dev.mateuy.safanoria.core.TicketGraph
import dev.mateuy.safanoria.core.TicketType
import dev.mateuy.safanoria.core.Version

/** What the versions screen shows: the versions of one component, with the tickets each one shipped. */
data class VersionsViewState(
    /** The project's components, to choose from; nothing to choose with only one. */
    val components: List<String> = emptyList(),
    /** The component whose versions are shown; null when the project has none. */
    val component: String? = null,
    /** What is not released yet first, then newest version first. Only versions with tickets. */
    val releases: List<VersionGroup> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * The tickets one version of the component shipped (`resolvedIn`, SPEC §9). [version] is null for
 * the `done` tickets still to release in it: what its next version brings.
 */
data class VersionGroup(val version: String?, val tickets: List<VersionTicket>) {
    /** Tickets in the version, children included. */
    val count: Int get() = tickets.sumOf { 1 + it.children.size }
}

data class VersionTicket(
    val id: String,
    val title: String,
    val type: TicketType?,
    /** Its parent, when that one is not in this version: this ticket shipped before it. */
    val parentId: String? = null,
    /** Its children that shipped in this version too, in the parent's Plan order. */
    val children: List<VersionTicket> = emptyList(),
)

/**
 * The tickets of [component] (the project's first when null or unknown) grouped by the version
 * that shipped them: its `done` tickets still to release first, then its versions, newest first,
 * ordered as versions. Within a group tickets are in id order, as `safanoria-cli notes` lists
 * them, with children under their parent when both are in the group.
 */
fun versionsViewState(snapshot: TicketsSnapshot, component: String? = null): VersionsViewState {
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
