package dev.mateuy.safanoria.core

import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BodyTest {
    private val text = """
        ---
        id: herd-locations
        type: feature
        title: Herd locations
        status: in-progress
        priority: high
        size: L
        created: 2026-10-01
        updated: 2026-10-02
        requests:
          - user: 1834
            channel: whatsapp
            date: 2026-09-28
        ---

        ## Objective

        Where each herd is.

        ```markdown
        ## Not a heading (inside a code block)
        ```

        ## User Requests

        > M'agradaria veure on és cada ramat.
        > Amb un mapa.
        — user 1834 · whatsapp · 2026-09-28

        ## Acceptance Criteria

        - [x] A herd has a location
        - [ ] The location shows on a map

        Out of scope:
        - Offline maps

        ## Plan

        Children merge into this branch.

        - [x] `herd-locations-model`: data first.
        - [ ] `herd-locations-map-input`: riskiest, so early.
              Decision: maplibre.
        - [X] Own step: docs.

        ## Custom Section

        Kept as is.

        ## Learnings

        - maplibre-compose on desktop needs Java 25.
          → promoted: CLAUDE.md, docs/maps.md
        - Demo map tiles are rate-limited.
          → new ticket: `map-tile-provider`
        - The demo account has only sheep.
          → ticket only
        - Tiles cache in ~/.cache.

        ## Work Log

        - **2026-10-01** · status · Started.
        - **2026-10-02** · step 2 · Deviation: movements must not change animals;
          added a criterion.
    """.trimIndent()

    private val ticket = Ticket("/repo/tickets/herd-locations.md".toPath(), text)
    private val body = ticket.body

    @Test
    fun sectionsSkipCodeBlocks() {
        assertEquals(
            listOf("Objective", "User Requests", "Acceptance Criteria", "Plan", "Custom Section", "Learnings", "Work Log"),
            body.sections.map { it.name },
        )
        assertEquals(16, body.section("Objective")!!.headingLine)
        assertEquals(17, body.section("Objective")!!.firstLine)
        assertEquals(23, body.section("Objective")!!.lastLine)
    }

    @Test
    fun checklists() {
        val criteria = body.checklist("Acceptance Criteria")
        assertEquals(listOf(true, false), criteria.map { it.checked })
        assertEquals("The location shows on a map", criteria[1].text)

        val plan = body.checklist("Plan")
        assertEquals(listOf("herd-locations-model", "herd-locations-map-input", null), plan.map { it.childId })
        assertEquals(listOf(true, false, true), plan.map { it.checked })
        assertEquals(plan[1].line + 1, plan[1].lastLine)
    }

    @Test
    fun learningsWithResolution() {
        val learnings = body.learnings
        assertEquals(4, learnings.size)
        assertEquals(Resolution.Promoted("CLAUDE.md, docs/maps.md"), learnings[0].resolution)
        assertEquals(Resolution.NewTicket("map-tile-provider"), learnings[1].resolution)
        assertEquals(Resolution.TicketOnly, learnings[2].resolution)
        assertEquals(Resolution.Pending, learnings[3].resolution)
        assertEquals(learnings[0].line + 1, learnings[0].resolutionLine)
    }

    @Test
    fun workLog() {
        val log = body.workLog
        assertEquals(listOf("status", "step 2"), log.map { it.ref })
        assertEquals("Deviation: movements must not change animals;\nadded a criterion.", log[1].text)
        assertEquals(log[1].line + 1, log[1].lastLine)
    }

    @Test
    fun quotes() {
        val q = body.quotes.single()
        assertEquals("M'agradaria veure on és cada ramat.\nAmb un mapa.", q.text)
        assertEquals(listOf("user 1834", "whatsapp", "2026-09-28"), listOf(q.ref, q.channel, q.date))
        assertEquals(q.line + 2, q.attributionLine)
    }

    @Test
    fun wellFormedHasNoDiagnostics() = assertEquals(emptyList(), ticket.parseDiagnostics)

    @Test
    fun malformedPartsBecomeDiagnostics() {
        val bad = text
            .replace("- **2026-10-01** · status · Started.", "- 2026-10-01 status Started.")
            .replace("→ ticket only", "→ done")
            .replace("— user 1834 · whatsapp · 2026-09-28", "— user 1834")
        val codes = Ticket("/t.md".toPath(), bad).parseDiagnostics.map { it.code to it.line }
        assertEquals(listOf("quote-attribution" to 28, "learning-resolution" to 58, "work-log-entry" to 63), codes.sortedBy { it.second })
    }

    @Test
    fun repositoryTickets() {
        val tickets = ticketFiles(repoRoot).map { Ticket(it, read(it)) }
        assertTrue(tickets.size >= 12)
        for (t in tickets) assertEquals(emptyList(), t.parseDiagnostics.map { it.toString() })

        val parent = tickets.single { it.fileId == "v1-tooling" }
        val children = tickets.filter { it.frontmatter?.parent?.value == "v1-tooling" }.map { it.fileId }.toSet()
        assertEquals(children, parent.body.checklist("Plan").mapNotNull { it.childId }.toSet())

        val spike = tickets.single { it.fileId == "v1-tooling-native-spike" }
        assertTrue(spike.body.learnings.size >= 9)
        assertTrue(spike.body.learnings.none { it.resolution == Resolution.Pending })
    }

    @Test
    fun extraRepositoriesParse() {
        for (root in extraRepos) {
            val tickets = ticketFiles(root).map { Ticket(it, read(it)) }
            val diagnostics = tickets.flatMap { it.parseDiagnostics }
            // Other projects' tickets may break the format; report, don't fail.
            println("$root: ${tickets.size} tickets, ${diagnostics.size} parse diagnostics")
            diagnostics.forEach { println("  $it") }
            tickets.forEach { it.body.sections; it.body.workLog; it.body.learnings }
        }
    }
}
