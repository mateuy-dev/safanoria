package dev.mateuy.safanoria.core

public data class NotesRequest(
    val component: String,
    /** `MAJOR.MINOR.PATCH`: the version to describe, or with [upTo] the one the reader already has. */
    val version: String,
    /** `MAJOR.MINOR.PATCH`: describe what changed after [version], up to and including this one. */
    val upTo: String? = null,
)

/** The tickets stamped with one version of a component, in id order. */
public data class ReleasedTickets(val version: Version, val tickets: List<Ticket>)

public sealed interface NotesResult {
    public data class Ready(
        val component: String,
        /** The version the range starts after; null when one version was asked for. */
        val after: Version?,
        val version: Version,
        /** Newest version first; versions without tickets are absent. */
        val releases: List<ReleasedTickets>,
        /** Every version stamped for the component, ascending: what to offer when [releases] is empty. */
        val stamped: List<Version>,
    ) : NotesResult

    public data class Refused(val reason: String) : NotesResult
}

/**
 * The tickets a version of a component shipped, by `resolvedIn.<c>` (SPEC §9), as the material to
 * write its release notes from. Reads the checkout: stamps are made on `mainBranch`.
 */
public object ReleaseNotes {
    public fun collect(repository: Repository, request: NotesRequest): NotesResult {
        val c = request.component
        val components = repository.config.components.keys
        if (c !in components) return NotesResult.Refused("'$c' is not a component in $CONFIG_FILE: ${components.joinToString()}")
        val version = parse(request.version) ?: return notAVersion(request.version)
        val upTo = request.upTo?.let { parse(it) ?: return notAVersion(it) }
        if (upTo != null && upTo <= version) return NotesResult.Refused("$upTo is not later than $version: give the older version first")

        val byVersion = repository.tickets
            .mapNotNull { t -> t.frontmatter?.resolvedIn?.get(c)?.value?.let(Version::parse)?.let { it to t } }
            .groupBy({ it.first }, { it.second })
        val wanted: (Version) -> Boolean = if (upTo == null) { v -> v == version } else { v -> v > version && v <= upTo }
        val releases = byVersion.filterKeys(wanted)
            .map { (v, tickets) -> ReleasedTickets(v, tickets.sortedBy { it.fileId }) }
            .sortedByDescending { it.version }
        return NotesResult.Ready(c, version.takeIf { upTo != null }, upTo ?: version, releases, byVersion.keys.sorted())
    }

    private fun parse(text: String) = Version.parse(text)
    private fun notAVersion(text: String) = NotesResult.Refused("'$text' is not a version: MAJOR.MINOR.PATCH, e.g. 4.3.0")
}
