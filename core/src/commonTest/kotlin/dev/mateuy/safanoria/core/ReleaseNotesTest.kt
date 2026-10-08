package dev.mateuy.safanoria.core

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReleaseNotesTest {
    private val root = "/repo".toPath()

    private fun ticket(id: String, front: String, status: String = "done") =
        "---\nid: $id\ntype: feature\ntitle: Ticket $id\nstatus: $status\npriority: medium\nsize: S\n" +
            "created: 2026-10-01\nupdated: 2026-10-01\n$front---\n\n## Objective\n\n<!-- what and why -->\nWhy $id.\n\n### Detail\n\nMore.\n\n" +
            "## Acceptance Criteria\n\n- [x] It works\n\n## Plan\n\n- [x] Do it\n\n" +
            "## Work Log\n\n- **2026-10-01** · status · Started.\n"

    private val fs = FakeFileSystem().apply {
        createDirectories(root / "tickets")
        write(root / "safanoria.yaml") { writeUtf8("safanoria: 1\nworktree: ../project--{id}\ncomponents:\n  app:\n    external: true\n  ktor:\n    external: true\n") }
        mapOf(
            "old" to ticket("old", "area: [app]\nresolvedIn:\n  app: 4.1.0\n"),
            "map" to ticket("map", "area: [app]\nresolvedIn:\n  app: 4.2.0\n"),
            "export" to ticket("export", "area: [app]\nresolvedIn:\n  app: 4.2.1\n"),
            "both" to ticket("both", "area: [app, ktor]\nresolvedIn:\n  app: 4.3.0\n  ktor: 1.9.0\n"),
            "another" to ticket("another", "area: [app]\nresolvedIn:\n  app: 4.3.0\n"),
            "unreleased" to ticket("unreleased", "area: [app]\nresolvedIn:\n  app: null\n"),
            "open" to ticket("open", "area: [app]\n", status = "review"),
        ).forEach { (id, text) -> write(root / "tickets" / "$id.md") { writeUtf8(text) } }
    }

    private fun collect(component: String, version: String, upTo: String? = null) =
        ReleaseNotes.collect(Repository(root, fs), NotesRequest(component, version, upTo))
    private fun ready(component: String, version: String, upTo: String? = null) = assertIs<NotesResult.Ready>(collect(component, version, upTo))
    private fun refused(component: String, version: String, upTo: String? = null) = assertIs<NotesResult.Refused>(collect(component, version, upTo)).reason
    private fun NotesResult.Ready.ids() = releases.map { r -> "${r.version}: ${r.tickets.joinToString { it.fileId }}" }

    @Test
    fun oneVersion() {
        assertEquals(listOf("4.3.0: another, both"), ready("app", "4.3.0").ids())
        assertEquals(listOf("1.9.0: both"), ready("ktor", "1.9.0").ids())
        val none = ready("app", "4.2.5")
        assertEquals(emptyList(), none.releases)
        assertEquals("4.1.0, 4.2.0, 4.2.1, 4.3.0", none.stamped.joinToString())
    }

    @Test
    fun betweenVersionsExcludesTheFirstAndIncludesTheLast() {
        assertEquals(listOf("4.3.0: another, both", "4.2.1: export"), ready("app", "4.2.0", "4.3.0").ids())
        // The ends need not be stamped versions.
        assertEquals(listOf("4.2.1: export", "4.2.0: map"), ready("app", "4.1.5", "4.2.9").ids())
        assertEquals(Version(4, 2, 0), ready("app", "4.2.0", "4.3.0").after)
    }

    @Test
    fun refusals() {
        assertTrue(refused("web", "1.0.0").contains("not a component"))
        assertTrue(refused("app", "v4.3").contains("not a version"))
        assertTrue(refused("app", "4.2.0", "latest").contains("not a version"))
        assertTrue(refused("app", "4.3.0", "4.2.0").contains("older version first"))
        assertTrue(refused("app", "4.3.0", "4.3.0").contains("not later"))
    }

    @Test
    fun sectionTextDropsCommentsAndKeepsSubheadings() {
        val t = Repository(root, fs).ticket("map")!!
        assertEquals("Why map.\n\n### Detail\n\nMore.", t.body.text(t.body.section("Objective")!!))
    }
}
