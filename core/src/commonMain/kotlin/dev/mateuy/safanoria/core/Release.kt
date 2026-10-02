package dev.mateuy.safanoria.core

public data class ReleaseRequest(
    val component: String,
    /** `MAJOR.MINOR.PATCH`; null to read it from the component's `version` source. */
    val version: String? = null,
    /** Stamp only these tickets (for external components, §9); null for every eligible one. */
    val only: List<String>? = null,
)

public sealed interface ReleaseResult {
    public data class Ready(
        val component: String,
        val version: Version,
        /** True when read from the component's source, false when given. */
        val versionFromSource: Boolean,
        /** Ids of the stamped tickets, in id order; empty when there is nothing to stamp. */
        val stamped: List<String>,
        val files: List<PlannedFile>,
        val warnings: List<String>,
    ) : ReleaseResult

    public data class Refused(val reason: String) : ReleaseResult
}

/**
 * Stamping (SPEC §9): `resolvedIn.<c>: <v>` and a `release · <c> <v>` Work Log entry on every
 * `done` ticket with `<c>` in its area and no version for it yet. Writes nothing: the caller
 * writes [ReleaseResult.Ready.files]. All or nothing: any refusal stamps no ticket.
 */
public object Release {
    public fun prepare(repository: Repository, request: ReleaseRequest, today: String): ReleaseResult {
        val config = repository.config
        val c = request.component
        val component = config.components[c]
            ?: return refused("'$c' is not a component in $CONFIG_FILE: ${config.components.keys.joinToString()}")

        val warnings = mutableListOf<String>()
        val fromSource = component.version?.let { Versions.read(repository, it) }
        val version = when {
            request.version != null -> {
                val given = Version.parse(request.version)
                    ?: return refused("'${request.version}' is not a version: MAJOR.MINOR.PATCH, e.g. 4.3.0")
                if (fromSource is VersionRead.Found && fromSource.version != given) {
                    warnings += "${component.version!!.file} says ${fromSource.version}, stamping $given as given"
                }
                given
            }
            component.external -> return refused("'$c' is external: give the version it was released as (§9)")
            fromSource == null -> return refused("'$c' has no version source in $CONFIG_FILE: give the version")
            fromSource is VersionRead.Failed -> return refused("can't read the version of '$c': ${fromSource.reason}")
            else -> (fromSource as VersionRead.Found).version
        }

        // Versions only go up per component: stamping 4.2.9 after 4.3.0 would say older releases had the change.
        val latest = repository.tickets.mapNotNull { t -> t.frontmatter?.resolvedIn?.get(c)?.value?.let(Version::parse) }.maxOrNull()
        if (latest != null && version < latest) return refused("$c $version is lower than $latest, already stamped on other tickets")

        val eligible = repository.tickets.filter { reasonNotEligible(it, c, config) == null }
        val tickets = if (request.only == null) eligible else {
            request.only.distinct().map { id ->
                val t = repository.ticket(id) ?: return refused("no ticket '$id'")
                reasonNotEligible(t, c, config)?.let { return refused("'$id' can't be stamped with $c: $it") }
                t
            }.sortedBy { it.fileId }
        }

        val files = tickets.map { t ->
            val text = try {
                TicketEditor(t.text)
                    .setMapEntry("resolvedIn", c, version.toString())
                    .setField("updated", today)
                    .appendWorkLog(today, "release", "$c $version")
                    .text
            } catch (e: TicketEditException) {
                return refused("can't stamp '${t.fileId}': ${e.message}")
            }
            PlannedFile(t.path, text, isNew = false)
        }
        return ReleaseResult.Ready(c, version, request.version == null, tickets.map { it.fileId }, files, warnings)
    }

    /** Null when [ticket] gets `resolvedIn.<component>` in this release (§9), else why not. */
    private fun reasonNotEligible(ticket: Ticket, component: String, config: Config): String? {
        val f = ticket.frontmatter ?: return "its frontmatter can't be read"
        if (f.status != Status.DONE) return "it is ${f.status?.text ?: "not done"}, not done"
        if (f.type == TicketType.RESEARCH) return "research tickets never get resolvedIn"
        val area = f.area.map { it.value }.ifEmpty { config.components.keys.toList().takeIf { it.size == 1 } ?: emptyList() }
        if (component !in area) return "'$component' is not in its area (${area.joinToString().ifEmpty { "none" }})"
        f.resolvedIn?.get(component)?.value?.let { return "it already has $component $it" }
        return null
    }

    private fun refused(reason: String) = ReleaseResult.Refused(reason)
}
