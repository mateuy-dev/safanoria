package dev.mateuy.safanoria.gui

import dev.mateuy.safanoria.gui.data.TicketsSnapshot
import dev.mateuy.safanoria.gui.versions.VersionsViewState
import dev.mateuy.safanoria.gui.versions.versionsViewState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersionsViewStateTest {
    private fun shipped(id: String, version: String, component: String = "app", extra: String = "") =
        ticket(id, status = "done", extra = "area: [$component]\nresolvedIn:\n  $component: $version" + if (extra.isEmpty()) "" else "\n$extra")

    private fun VersionsViewState.ids(version: String?) = releases.single { it.version == version }.tickets.map { it.id }

    @Test
    fun loadingUntilTheFirstReadAndAFailedOneShowsTheError() {
        assertTrue(versionsViewState(TicketsSnapshot()).loading)
        val failed = versionsViewState(TicketsSnapshot(error = "not a git repository"))
        assertFalse(failed.loading)
        assertEquals("not a git repository", failed.error)
    }

    @Test
    fun versionsAreNewestFirstOrderedAsVersions() {
        val state = versionsViewState(
            snapshotOf(shipped("in-nine", "0.9.0"), shipped("in-ten", "0.10.0"), shipped("in-two", "0.2.0"), shipped("also-ten", "0.10.0"), components = listOf("app")),
        )
        assertEquals(listOf("0.10.0", "0.9.0", "0.2.0"), state.releases.map { it.version })
        assertEquals(listOf("also-ten", "in-ten"), state.ids("0.10.0"))
        assertEquals("app", state.component)
    }

    @Test
    fun doneTicketsNotReleasedYetAreOnTop() {
        val state = versionsViewState(
            snapshotOf(
                shipped("old-one", "1.0.0"),
                ticket("waiting", status = "done", extra = "area: [app]"),
                ticket("study", status = "done", type = "research", extra = "area: [app]"),
                ticket("open-one", status = "in-progress", extra = "area: [app]"),
                components = listOf("app"),
            ),
        )
        assertEquals(listOf(null, "1.0.0"), state.releases.map { it.version })
        assertEquals(listOf("waiting"), state.ids(null))
    }

    @Test
    fun noUnreleasedGroupWhenEverythingIsReleased() {
        val state = versionsViewState(snapshotOf(shipped("old-one", "1.0.0"), ticket("open-one"), components = listOf("app")))
        assertEquals(listOf<String?>("1.0.0"), state.releases.map { it.version })
    }

    @Test
    fun theSelectedComponentsVersionsAreShown() {
        val snapshot = snapshotOf(
            shipped("app-one", "4.3.0"),
            shipped("web-one", "2.0.0", component = "web"),
            ticket("both", status = "done", extra = "area: [app, web]\nresolvedIn:\n  app: 4.3.0"),
            components = listOf("app", "web"),
        )
        val app = versionsViewState(snapshot)
        assertEquals(listOf("app", "web"), app.components)
        assertEquals("app", app.component)
        assertEquals(listOf<String?>("4.3.0"), app.releases.map { it.version })
        assertEquals(listOf("app-one", "both"), app.ids("4.3.0"))

        val web = versionsViewState(snapshot, "web")
        assertEquals("web", web.component)
        assertEquals(listOf(null, "2.0.0"), web.releases.map { it.version })
        assertEquals(listOf("both"), web.ids(null))

        assertEquals("app", versionsViewState(snapshot, "gone").component)
    }

    @Test
    fun childrenAreNestedUnderTheirParentInTheSameVersion() {
        val state = versionsViewState(
            snapshotOf(
                shipped("parent-one", "2.0.0", extra = ""),
                shipped("zz-first", "2.0.0", extra = "parent: parent-one"),
                shipped("aa-second", "2.0.0", extra = "parent: parent-one"),
                shipped("early-child", "1.5.0", extra = "parent: parent-one"),
                shipped("other", "2.0.0"),
                components = listOf("app"),
            ),
        )
        assertEquals(listOf("other", "parent-one"), state.ids("2.0.0"))
        val group = state.releases.single { it.version == "2.0.0" }
        val parent = group.tickets.single { it.id == "parent-one" }
        assertEquals(listOf("aa-second", "zz-first"), parent.children.map { it.id })
        assertNull(parent.children.first().parentId)
        assertEquals(4, group.count)
        // Shipped before its parent: a row in its own version, labelled with the parent.
        val early = state.releases.single { it.version == "1.5.0" }.tickets.single()
        assertEquals("early-child" to "parent-one", early.id to early.parentId)
    }

    @Test
    fun nothingToShowWithoutComponentsOrVersions() {
        assertNull(versionsViewState(snapshotOf(ticket("one"))).component)
        assertEquals(emptyList(), versionsViewState(snapshotOf(ticket("one"), components = listOf("app"))).releases)
    }
}
