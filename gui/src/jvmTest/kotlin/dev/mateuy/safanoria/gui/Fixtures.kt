package dev.mateuy.safanoria.gui

import dev.mateuy.safanoria.core.Component
import dev.mateuy.safanoria.core.Config
import dev.mateuy.safanoria.core.Diagnostic
import dev.mateuy.safanoria.core.Severity
import dev.mateuy.safanoria.core.Ticket
import dev.mateuy.safanoria.core.TicketGraph
import dev.mateuy.safanoria.gui.data.TicketsSnapshot
import dev.mateuy.safanoria.gui.data.snapshot
import okio.Path.Companion.toPath

internal val TICKET_DIR = "/project/tickets".toPath()

/** A ticket as text, the way the app reads them. [extra] is more frontmatter lines; [plan] the Plan's items. */
internal fun ticket(
    id: String,
    status: String = "backlog",
    type: String = "feature",
    priority: String = "medium",
    extra: String = "",
    plan: String = "",
    branch: String? = null,
    onlyOnBranch: Boolean = false,
): Ticket = Ticket(
    TICKET_DIR / "$id.md",
    (
        listOf("---", "id: $id", "type: $type", "title: Title of $id", "status: $status", "priority: $priority", "size: S") +
            listOf("created: 2026-10-01", "updated: 2026-10-02") + listOfNotNull(extra.ifEmpty { null }) +
            listOf("---", "", "## Objective", "", "Why $id.", "", "## Plan", "", plan, "", "## Work Log", "")
        ).joinToString("\n"),
    branch,
    onlyOnBranch,
)

internal fun problem(id: String, code: String, severity: Severity = Severity.ERROR, line: Int? = 3) =
    Diagnostic(TICKET_DIR / "$id.md", line, null, code, "message of $code", severity)

/** [components] are the project's, as in `safanoria.yaml`. */
internal fun snapshotOf(
    vararg tickets: Ticket,
    diagnostics: List<Diagnostic> = emptyList(),
    checkoutBranch: String? = null,
    components: List<String> = emptyList(),
): TicketsSnapshot {
    val config = Config(
        "/project/safanoria.yaml".toPath(), specVersion = 1,
        components = components.associateWith { Component(it, version = null, external = false, line = 1) },
    )
    return snapshot(TicketGraph(tickets.toList()), config, diagnostics, TICKET_DIR, checkoutBranch)
}
