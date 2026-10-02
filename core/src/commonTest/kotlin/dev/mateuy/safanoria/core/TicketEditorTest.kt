package dev.mateuy.safanoria.core

import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TicketEditorTest {
    private val ticket = """
        ---
        id: map-input
        type: feature
        title: Pick a location on a map
        status: backlog   # waiting for tiles
        priority: high
        size: S
        created: 2026-10-01
        updated: 2026-10-01
        estimate: 3d
        ---

        ## Objective

        Map.

        ## Plan

        - [ ] First
        - [x] Second

        ## Work Log

        - **2026-10-01** · status · Created.

        ## Notes

        Unknown section, kept.
    """.trimIndent() + "\n"

    private fun lines(text: String) = text.split('\n')

    /** The edited text equals the original except at [changed] (indexes) and [inserted] lines. */
    private fun assertOnlyChanged(before: String, after: String, changed: Set<Int> = emptySet(), inserted: Set<Int> = emptySet()) {
        val a = lines(after).filterIndexed { i, _ -> i !in inserted }
        val b = lines(before)
        assertEquals(b.size, a.size, "line count after removing inserted lines")
        val differing = b.indices.filter { a[it] != b[it] }.toSet()
        assertEquals(changed, differing)
    }

    @Test
    fun noEditsIsTheSameText() = assertEquals(ticket, TicketEditor(ticket).text)

    @Test
    fun setFieldReplacesOnlyItsLineAndKeepsComments() {
        val after = TicketEditor(ticket).setField("status", "in-progress").text
        assertTrue(after.contains("status: in-progress   # waiting for tiles\n"))
        assertOnlyChanged(ticket, after, changed = setOf(4))
    }

    @Test
    fun setFieldInsertsAtSpecPosition() {
        val after = TicketEditor(ticket).setField("parent", "herd-locations").text
        // After `updated` (§5 order), before the unknown `estimate`.
        assertEquals("parent: herd-locations", lines(after)[9])
        assertOnlyChanged(ticket, after, inserted = setOf(9))
    }

    @Test
    fun valuesAreQuotedWhenNeeded() {
        val e = TicketEditor(ticket)
        e.setField("title", "Map: pick a place")
        e.setField("assignee", "true")
        val f = Frontmatter.parse(null, e.text).first!!
        assertEquals("Map: pick a place", f.title!!.value)
        assertEquals("true", f.assignee!!.value)
        assertTrue(e.text.contains("title: \"Map: pick a place\""))
        assertEquals(emptyList(), f.schemaDiagnostics())
    }

    @Test
    fun setListReplacesBlockLists() {
        val start = TicketEditor(ticket).setField("parent", "p").text.replace("parent: p\n", "blockedBy:\n  - a\n  - b\n")
        val after = TicketEditor(start).setList("blockedBy", listOf("a", "c")).text
        assertTrue(after.contains("blockedBy: [a, c]\nestimate: 3d"))
        assertEquals(listOf("a", "c"), Frontmatter.parse(null, after).first!!.blockedBy.map { it.value })
    }

    @Test
    fun setMapEntryForResolvedIn() {
        // Absent → block after `updated` (resolvedIn is last in §5 order among present fields).
        val added = TicketEditor(ticket).setField("status", "done").setMapEntry("resolvedIn", "app", "4.3.0").text
        assertTrue(added.contains("updated: 2026-10-01\nresolvedIn:\n  app: 4.3.0\nestimate: 3d"))
        // null → block.
        val fromNull = TicketEditor(ticket.replace("estimate: 3d", "resolvedIn: null")).setMapEntry("resolvedIn", "app", "4.3.0").text
        assertTrue(fromNull.contains("resolvedIn:\n  app: 4.3.0\n---"))
        // Existing entry replaced, new one appended.
        val twice = TicketEditor(fromNull).setMapEntry("resolvedIn", "app", "4.4.0").setMapEntry("resolvedIn", "ktor", "2.0.0").text
        assertTrue(twice.contains("resolvedIn:\n  app: 4.4.0\n  ktor: 2.0.0\n---"))
        val f = Frontmatter.parse(null, twice).first!!
        assertEquals(mapOf("app" to "4.4.0", "ktor" to "2.0.0"), f.resolvedIn!!.mapValues { it.value.value })
    }

    @Test
    fun refusesWhatItCannotEditSafely() {
        assertFailsWith<TicketEditException> {
            TicketEditor(ticket.replace("title: Pick a location on a map", "title: >\n  Pick a location\n  on a map")).setField("title", "x")
        }
        assertFailsWith<TicketEditException> {
            TicketEditor(ticket.replace("estimate: 3d", "resolvedIn: { app: 1.0.0 }")).setMapEntry("resolvedIn", "ktor", "1.0.0")
        }
        assertFailsWith<TicketEditException> { TicketEditor(ticket).setChecked(2, true) }
        assertFailsWith<TicketEditException> { TicketEditor("---\nid: x\n---\n").appendWorkLog("2026-10-02", "status", "x") }
        assertFailsWith<TicketEditException> { TicketEditor("no frontmatter\n").setField("id", "x") }
    }

    @Test
    fun setCheckedChangesOnlyTheCheckbox() {
        val after = TicketEditor(ticket).setChecked(19, true).setChecked(20, false).text
        assertTrue(after.contains("- [x] First\n- [ ] Second"))
        assertOnlyChanged(ticket, after, changed = setOf(18, 19))
    }

    @Test
    fun appendWorkLogAtTheEndOfTheSection() {
        val after = TicketEditor(ticket).appendWorkLog("2026-10-02", "step 1", "Done.\nSecond line.").text
        assertTrue(after.contains("- **2026-10-01** · status · Created.\n- **2026-10-02** · step 1 · Done.\n  Second line.\n\n## Notes"))
        assertOnlyChanged(ticket, after, inserted = setOf(24, 25))
        val log = Ticket("t.md".toPath(), after).body.workLog
        assertEquals("Done.\nSecond line.", log.last().text)
    }

    @Test
    fun appendWorkLogToAnEmptySection() {
        val empty = ticket.replace("- **2026-10-01** · status · Created.\n", "")
        val after = TicketEditor(empty).appendWorkLog("2026-10-02", "status", "Started.").text
        assertTrue(after.contains("## Work Log\n\n- **2026-10-02** · status · Started.\n\n## Notes"), after)
    }

    @Test
    fun keepsCrlfAndMissingFinalNewline() {
        val crlf = ticket.trimEnd('\n').replace("\n", "\r\n")
        val after = TicketEditor(crlf).setField("status", "ready").appendWorkLog("2026-10-02", "status", "Ready.").text
        assertTrue(after.split("\r\n").none { it.contains('\n') }, "every line ends with CRLF")
        assertTrue(!after.endsWith("\n"))
        assertEquals(crlf, TicketEditor(crlf).text)
    }

    /** No-op round trip and targeted edits on every real ticket (this repository and extra ones). */
    @Test
    fun realTickets() {
        for (root in listOf(repoRoot) + extraRepos) {
            for (file in ticketFiles(root)) {
                val text = read(file)
                assertEquals(text, TicketEditor(text).text, "$file round trip")

                val updated = TicketEditor(text).setField("updated", "2026-12-31").text
                val index = lines(text).indexOfFirst { it.startsWith("updated:") }
                assertOnlyChanged(text, updated, changed = setOf(index))
                assertEquals("2026-12-31", Frontmatter.parse(file, updated).first!!.updated!!.value)

                val logged = TicketEditor(text).appendWorkLog("2026-12-31", "test", "Appended.").text
                val entries = Ticket(file, logged).body.workLog
                assertEquals("Appended.", entries.last().text, "$file work log")
                assertEquals(Ticket(file, text).body.workLog.size + 1, entries.size)

                val stamped = TicketEditor(text).setMapEntry("resolvedIn", "app", "9.9.9").text
                assertEquals("9.9.9", Frontmatter.parse(file, stamped).first!!.resolvedIn!!["app"]!!.value, "$file resolvedIn")
            }
        }
    }

    @Test
    fun replaceSectionIntroKeepsSubsections() {
        val bug = "## Objective\n\nWhat is wrong.\n\n### Steps to reproduce\n\n1. Open\n\n## Plan\n"
        assertEquals(
            "## Objective\n\nSaving crashes.\n\n### Steps to reproduce\n\n1. Open\n\n## Plan\n",
            TicketEditor(bug).replaceSectionIntro("Objective", "Saving crashes.").text,
        )
        assertEquals(
            "## Objective\n\nNew.\n\n## Plan\n",
            TicketEditor("## Objective\n\nOld.\n\n## Plan\n").replaceSectionIntro("Objective", "New.").text,
        )
    }
}
