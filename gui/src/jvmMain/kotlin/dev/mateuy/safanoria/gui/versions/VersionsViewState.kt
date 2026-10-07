package dev.mateuy.safanoria.gui.versions

import dev.mateuy.safanoria.core.TicketType

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
