package dev.mateuy.safanoria.core

import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrontmatterTest {
    private val file = "/repo/tickets/map-input.md".toPath()

    private val ticket = """
        ---
        id: map-input
        type: feature
        title: Pick a location on a map
        status: in-progress
        priority: high
        size: XL
        created: 2026-10-01
        updated: 2026-10-02
        parent: herd-locations
        blockedBy:
          - map-tiles
          - geo-search
        requests:
          - user: 1834
            channel: whatsapp
            date: 2026-09-28
        resolvedIn: null
        custom: kept
        ---

        ## Objective
    """.trimIndent()

    private fun parse(text: String) = Frontmatter.parse(file, text).let { (f, d) -> assertTrue(d.isEmpty(), d.toString()); f!! }

    @Test
    fun typedFieldsWithLines() {
        val f = parse(ticket)
        assertEquals(Located("map-input", 2, 5), f.id)
        assertEquals(TicketType.FEATURE, f.type)
        assertEquals(Status.IN_PROGRESS, f.status)
        assertEquals("in-progress", f.status!!.text)
        assertEquals(Size.XL, f.size)
        assertEquals("XL", f.size!!.text)
        assertEquals(Located("herd-locations", 10, 9), f.parent)
        assertEquals(listOf("map-tiles" to 12, "geo-search" to 13), f.blockedBy.map { it.value to it.line })
        assertEquals(Request("1834", null, "whatsapp", "2026-09-28", 15), f.requests.single())
        assertNull(f.resolvedIn)
        assertEquals("custom", f.keys.last().value)
        assertEquals(19, f.keys.last().line)
        assertEquals(emptyList(), f.schemaDiagnostics())
    }

    @Test
    fun veryLowPriority() {
        val f = parse(ticket.replace("priority: high", "priority: very-low"))
        assertEquals(Priority.VERY_LOW, f.priority)
        assertEquals("very-low", f.priority!!.text)
        assertEquals(emptyList(), f.schemaDiagnostics())
    }

    @Test
    fun schemaDiagnosticsUseFileLines() {
        val bad = ticket.replace("status: in-progress", "status: almost").replace("  - geo-search", "  - Geo")
        val diagnostics = parse(bad).schemaDiagnostics()
        assertEquals(listOf(5 to "schema-enum", 13 to "schema-pattern"), diagnostics.map { it.line to it.code }.sortedBy { it.first })
        assertTrue(diagnostics.first().toString().startsWith("/repo/tickets/map-input.md:"))
    }

    @Test
    fun missingFieldPointsAtTheFrontmatter() {
        val d = parse(ticket.replace("title: Pick a location on a map\n", "")).schemaDiagnostics().single()
        assertEquals("schema-required", d.code)
        assertEquals(2, d.line)
    }

    @Test
    fun quotedNumbersAreStrings() {
        val f = parse(ticket.replace("title: Pick a location on a map", "title: \"2026\""))
        assertEquals(emptyList(), f.schemaDiagnostics())
        val plain = parse(ticket.replace("title: Pick a location on a map", "title: 2026"))
        assertEquals("schema-type", plain.schemaDiagnostics().single().code)
    }

    @Test
    fun resolvedInEntries() {
        val f = parse(ticket.replace("resolvedIn: null", "resolvedIn:\n  app: 4.3.0\n  ktor: null").replace("in-progress", "done"))
        assertEquals(mapOf("app" to "4.3.0", "ktor" to null), f.resolvedIn!!.mapValues { it.value.value })
        assertEquals(19, f.resolvedIn!!["app"]!!.line)
    }

    @Test
    fun crlfLineEndings() {
        val f = parse(ticket.replace("\n", "\r\n"))
        assertEquals(Located("map-input", 2, 5), f.id)
        assertEquals(emptyList(), f.schemaDiagnostics())
    }

    @Test
    fun missingOrUnclosedFrontmatter() {
        assertEquals("frontmatter-missing", Frontmatter.parse(file, "# Title\n").second.single().code)
        assertEquals("frontmatter-missing", Frontmatter.parse(file, "---\nid: x\n").second.single().code)
    }

    @Test
    fun yamlSyntaxErrorHasFileLine() {
        val (f, d) = Frontmatter.parse(file, "---\nid: x\ntype: [unclosed\nstatus: done\n---\n")
        assertNull(f)
        assertEquals("yaml-syntax", d.single().code)
        assertNotNull(d.single().line)
        assertTrue(d.single().line!! in 3..4, d.single().toString())
        // Line numbers inside kaml's message are file lines too.
        assertTrue(d.single().message.contains("at line 3, column"), d.single().message)
    }
}
