package dev.mateuy.safanoria.core

import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TicketGraphTest {
    private val valid = Repository(repoRoot / "core" / "src" / "commonTest" / "fixtures" / "validate" / "valid")

    private fun ticket(id: String, status: String, priority: String? = "medium", extra: String = "", plan: String = "") = Ticket(
        "/t/$id.md".toPath(),
        "---\nid: $id\ntype: feature\ntitle: $id\nstatus: $status\n" + (priority?.let { "priority: $it\n" } ?: "") +
            "size: S\ncreated: 2026-10-01\nupdated: 2026-10-01\n$extra---\n\n## Objective\n\nWhy.\n\n## Plan\n\n$plan",
    )

    private fun ids(tickets: List<Ticket>) = tickets.map { it.fileId }

    @Test
    fun validFixture() {
        val graph = valid.graph
        val parent = graph.ticket("herd-locations")!!
        val map = graph.ticket("herd-locations-map")!!
        val model = graph.ticket("herd-locations-model")!!

        assertEquals(listOf("herd-locations-model", "herd-locations-map"), ids(graph.children(parent)))
        assertEquals(parent, graph.parent(map))
        assertNull(graph.parent(parent))
        assertEquals(listOf("herd-locations-map"), ids(graph.blocks(model)))
        assertEquals(emptyList(), graph.openBlockers(map), "the blocker is done")
        assertEquals(Progress(1, 3), graph.progress(parent))
        assertNull(graph.progress(map), "empty Plan")
        assertEquals(Status.IN_PROGRESS, graph.tickets.first().frontmatter!!.status)
        assertEquals(Status.WONTFIX, graph.tickets.last().frontmatter!!.status)
    }

    @Test
    fun order() {
        val graph = TicketGraph(listOf(
            ticket("closed-one", "wontfix"),
            ticket("finished", "done"),
            ticket("later-low", "backlog", "low"),
            ticket("later-very-low", "backlog", "very-low"),
            ticket("later-none", "backlog", null),
            ticket("later-urgent", "backlog", "urgent"),
            ticket("b-ready", "ready"),
            ticket("a-ready", "ready"),
            ticket("reviewing", "review"),
            ticket("working", "in-progress"),
            ticket("broken", "nonsense"),
        ))
        assertEquals(
            listOf("working", "reviewing", "a-ready", "b-ready", "later-urgent", "later-low", "later-very-low", "later-none", "finished", "closed-one", "broken"),
            ids(graph.tickets),
        )
    }

    @Test
    fun relations() {
        val graph = TicketGraph(listOf(
            ticket("parent-one", "in-progress", plan = "- [ ] `parent-one-b`: b.\n- [x] `parent-one-a`: a.\n- [x] Own step.\n"),
            ticket("parent-one-a", "done", extra = "parent: parent-one\n"),
            ticket("parent-one-b", "ready", extra = "parent: parent-one\nblockedBy: [parent-one-a, parent-one-c, missing]\n"),
            ticket("parent-one-c", "backlog", extra = "parent: parent-one\n"),
            ticket("orphan", "backlog", extra = "parent: missing\nblockedBy: [parent-one-c, parent-one-c]\n"),
        ))
        val parent = graph.ticket("parent-one")!!
        val c = graph.ticket("parent-one-c")!!
        assertEquals(listOf("parent-one-b", "parent-one-a", "parent-one-c"), ids(graph.children(parent)), "Plan order, unlisted last")
        assertEquals(listOf("parent-one-c", "missing"), graph.openBlockers(graph.ticket("parent-one-b")!!))
        assertEquals(listOf("parent-one-c"), graph.openBlockers(graph.ticket("orphan")!!))
        assertEquals(listOf("parent-one-b", "orphan"), ids(graph.blocks(c)))
        assertNull(graph.parent(graph.ticket("orphan")!!))
        assertEquals(Progress(2, 3), graph.progress(parent))
    }
}
