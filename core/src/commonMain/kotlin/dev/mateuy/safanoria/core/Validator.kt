package dev.mateuy.safanoria.core

import okio.Path

/**
 * A problem and the files that cause it besides its own. In only-given-files mode a problem is
 * reported when its file or one of its causes is given.
 */
internal data class Finding(val diagnostic: Diagnostic, val causes: Set<Path> = emptySet())

/**
 * Checks a repository's checkout against SPEC §12. Codes are stable: tools and docs refer to
 * them. With [branches], ids on other branches count as known, and tickets created separately
 * on another branch are reported (SPEC §14.3).
 */
public class Validator(private val repository: Repository, private val branches: Branches? = null) {
    private val config get() = repository.config

    /**
     * All problems, or, when [only] is given, those in those files or caused by them. Paths are
     * compared after canonicalization. Every ticket is always loaded: cross-ticket rules need them.
     */
    public fun validate(only: Collection<Path>? = null): List<Diagnostic> {
        val findings = repository.configResult.diagnostics.map { Finding(it) } +
            repository.tickets.flatMap { ticketFindings(it) } +
            CrossTicketRules(repository.tickets, branches?.ids.orEmpty()).findings() +
            AttachmentRules(repository).findings() +
            createdTwice()
        val selected = only?.map { canonical(it) }?.toSet()
        return findings
            .filter { f -> selected == null || f.diagnostic.file?.let(::canonical) in selected || f.causes.any { canonical(it) in selected } }
            .map { it.diagnostic }
            .distinct()
            .sortedWith(compareBy({ it.file?.toString() }, { it.line ?: 0 }, { it.column ?: 0 }, { it.code }))
    }

    /** Tickets in this checkout whose id was also created separately on another branch (§14.3). */
    private fun createdTwice(): List<Finding> {
        val b = branches ?: return emptyList()
        val here = b.branches.first()
        return b.createdTwice().mapNotNull { twice ->
            val other = when (here) {
                twice.branch -> twice.otherBranch
                twice.otherBranch -> twice.branch
                else -> return@mapNotNull null
            }
            val ticket = repository.ticket(twice.id) ?: return@mapNotNull null
            Finding(Diagnostic(ticket.path, ticket.frontmatter?.id?.line ?: 1, null, "id-created-twice",
                "branch '$other' also created a ticket '${twice.id}' separately; they will conflict at merge, so rename one (§14.3)", Severity.WARNING))
        }
    }

    private fun canonical(path: Path): Path =
        if (repository.fileSystem.exists(path)) repository.fileSystem.canonicalize(path) else path

    // --- one ticket -----------------------------------------------------------------------------

    private fun ticketFindings(ticket: Ticket): List<Finding> {
        val out = mutableListOf<Diagnostic>()
        fun report(line: Int?, code: String, message: String, column: Int? = null) {
            out += Diagnostic(ticket.path, line, column, code, message)
        }

        out += ticket.parseDiagnostics
        val f = ticket.frontmatter ?: return out.map { Finding(it) }
        val body = ticket.body
        val status = f.status

        // id (§3)
        val id = f.id
        if (id != null && id.value != ticket.fileId) {
            report(id.line, "id-mismatch", "id '${id.value}' must equal the filename '${ticket.fileId}'", id.column)
        }

        // Schema (§5), with clearer messages where a rule says it better.
        val resolvedIn = f.resolvedIn
        for (e in f.schemaErrors()) {
            if (e.pointer == "/resolvedIn" && e.keyword == "type" && resolvedIn != null) continue // reported below
            out += SchemaValidator.toDiagnostic(f.block, e)
        }
        if (resolvedIn != null && resolvedIn.isNotEmpty()) {
            val line = f.keys.firstOrNull { it.value == "resolvedIn" }?.line
            when {
                f.type == TicketType.RESEARCH -> report(line, "resolved-in-not-allowed", "research tickets never get resolvedIn (§9)")
                status == Status.WONTFIX -> report(line, "resolved-in-not-allowed", "wontfix tickets never get resolvedIn (§9)")
                status != null && status != Status.DONE ->
                    report(line, "resolved-in-not-allowed", "resolvedIn is set only on done tickets, by release stamping; status is ${status.text} (§9)")
            }
        }

        // Components (§2, §5, §9)
        val components = config.components.keys
        if (components.isNotEmpty()) {
            for (a in f.area) if (a.value !in components) {
                report(a.line, "area-unknown-component", "'${a.value}' is not a component in ${CONFIG_FILE}: ${components.joinToString()}", a.column)
            }
            if (f.area.isEmpty() && components.size > 1) {
                report(id?.line ?: 1, "area-required", "area is required when the project has several components (${components.joinToString()})")
            }
            val area = f.area.map { it.value }.ifEmpty { components.toList().takeIf { it.size == 1 } ?: emptyList() }
            for ((component, version) in resolvedIn ?: emptyMap()) {
                when {
                    component !in components ->
                        report(version.line, "resolved-in-unknown-component", "'$component' is not a component in $CONFIG_FILE", version.column)
                    component !in area ->
                        report(version.line, "resolved-in-not-in-area", "'$component' is not in this ticket's area (${area.joinToString()})", version.column)
                }
            }
        }

        // Requests (§5, §7.3, §10)
        val requests = f.requests
        for (r in requests) if (r.channel != null && r.channel !in config.channels) {
            report(r.line, "channel-unknown", "channel '${r.channel}' is not in $CONFIG_FILE channels: ${config.channels.joinToString()}")
        }
        val quotes = body.quotes
        if (requests.size != quotes.size) {
            val line = body.section("User Requests")?.headingLine ?: f.keys.firstOrNull { it.value == "requests" }?.line
            report(line, "requests-quotes-mismatch", "${requests.size} requests in the frontmatter but ${quotes.size} quotes in User Requests (§7.3)")
        }

        // Sections (§7, §7.1)
        out += sectionDiagnostics(ticket, f, body)

        // review / done (§7.1, §7.6)
        if (status == Status.REVIEW || status == Status.DONE) {
            for (item in body.checklist("Plan")) if (!item.checked) {
                report(item.line, "plan-unchecked", "every Plan item must be checked at ${status.text}")
            }
            for (l in body.learnings) if (l.resolution == Resolution.Pending) {
                report(l.line, "learning-pending", "learnings must be resolved (→ promoted / new ticket / ticket only) at ${status.text} (§7.6)")
            }
        }
        return out.map { Finding(it) }
    }

    private fun sectionDiagnostics(ticket: Ticket, f: Frontmatter, body: Body): List<Diagnostic> {
        val out = mutableListOf<Diagnostic>()
        val lastLine = ticket.text.split('\n').size
        val standard = body.sections.filter { it.name in STANDARD_SECTIONS }

        // Duplicates and order: only standard sections are compared; others may go anywhere.
        val seen = mutableSetOf<String>()
        var previous = -1
        for (s in standard) {
            if (!seen.add(s.name)) {
                out += Diagnostic(ticket.path, s.headingLine, null, "section-duplicate", "'## ${s.name}' appears more than once")
                continue
            }
            val index = STANDARD_SECTIONS.indexOf(s.name)
            if (index < previous) {
                out += Diagnostic(ticket.path, s.headingLine, null, "section-order",
                    "'## ${s.name}' must come before '## ${STANDARD_SECTIONS[previous]}' (§7 order)")
            }
            previous = maxOf(previous, index)
        }

        val required = buildList {
            add("Objective")
            if (f.requests.isNotEmpty()) add("User Requests")
            add("Acceptance Criteria"); add("Plan"); add("Work Log")
        }
        for (name in required) if (body.section(name) == null) {
            // Point at where it belongs: the next standard section present, or the end.
            val next = STANDARD_SECTIONS.drop(STANDARD_SECTIONS.indexOf(name) + 1).firstNotNullOfOrNull { body.section(it) }
            out += Diagnostic(ticket.path, next?.headingLine ?: lastLine, null, "section-missing", "'## $name' is missing (§7)")
        }

        val status = f.status ?: return out
        val nonEmpty = buildList {
            add("Objective")
            if (status.ordinal >= Status.READY.ordinal && status != Status.WONTFIX) add("Acceptance Criteria")
            if (status.ordinal >= Status.IN_PROGRESS.ordinal && status != Status.WONTFIX) add("Plan")
            if (status.ordinal >= Status.IN_PROGRESS.ordinal) add("Work Log") // wontfix: the Work Log says why (§6.1)
        }
        for (name in nonEmpty) {
            val s = body.section(name) ?: continue
            if (!body.hasContent(s)) {
                out += Diagnostic(ticket.path, s.headingLine, null, "section-empty", "'## $name' must not be empty at ${status.text} (§7.1)")
            }
        }
        return out
    }
}
