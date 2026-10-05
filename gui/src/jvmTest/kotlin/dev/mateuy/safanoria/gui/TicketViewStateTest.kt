package dev.mateuy.safanoria.gui

import dev.mateuy.safanoria.core.Severity
import dev.mateuy.safanoria.core.Status
import dev.mateuy.safanoria.gui.data.TicketsSnapshot
import dev.mateuy.safanoria.gui.ticket.TicketAction
import dev.mateuy.safanoria.gui.ticket.TicketLink
import dev.mateuy.safanoria.gui.ticket.TicketProblem
import dev.mateuy.safanoria.gui.ticket.ticketViewState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TicketViewStateTest {
    @Test
    fun loadingUntilTheTicketsAreRead() {
        assertTrue(ticketViewState("any-id", TicketsSnapshot()).loading)
    }

    @Test
    fun anUnknownIdIsNotFound() {
        val state = ticketViewState("gone", snapshotOf(ticket("other")))
        assertFalse(state.found)
        assertFalse(state.loading)
    }

    @Test
    fun frontmatterBecomesTitleStatusAndFacts() {
        val state = ticketViewState("the-one", snapshotOf(ticket("the-one", status = "in-progress", type = "bug", extra = "area: [app, web]", branch = "the-one")))
        assertEquals("Title of the-one", state.title)
        assertEquals(Status.IN_PROGRESS, state.status)
        assertEquals(
            listOf(
                "type" to "bug", "priority" to "medium", "size" to "S", "area" to "app, web",
                "created" to "2026-10-01", "updated" to "2026-10-02", "read from" to "the-one",
            ),
            state.facts,
        )
    }

    @Test
    fun theBodyIsTheFileWithoutItsFrontmatter() {
        val state = ticketViewState("the-one", snapshotOf(ticket("the-one")))
        assertTrue(state.body.startsWith("## Objective\n\nWhy the-one."), state.body)
        assertFalse("id: the-one" in state.body)
    }

    @Test
    fun relationsLinkBothWays() {
        val snapshot = snapshotOf(
            ticket("parent-one", plan = "- [ ] `second`: later\n- [x] `first`: sooner"),
            ticket("first", status = "done", extra = "parent: parent-one"),
            ticket("second", extra = "parent: parent-one\nblockedBy: [first, missing]"),
        )
        val parent = ticketViewState("parent-one", snapshot)
        // Children in the parent's Plan order.
        assertEquals(listOf("second", "first"), parent.children.map { it.id })
        assertNull(parent.parent)

        val second = ticketViewState("second", snapshot)
        assertEquals(TicketLink("parent-one", "Title of parent-one", Status.BACKLOG), second.parent)
        assertEquals(
            listOf(TicketLink("first", "Title of first", Status.DONE), TicketLink("missing", null, null)),
            second.blockedBy,
        )
        assertEquals(listOf("second"), ticketViewState("first", snapshot).blocks.map { it.id })
    }

    @Test
    fun theActionsDependOnTheStatus() {
        fun actions(status: String) = ticketViewState("the-one", snapshotOf(ticket("the-one", status = status))).actions
        assertEquals(listOf(TicketAction.START), actions("backlog"))
        assertEquals(listOf(TicketAction.START), actions("ready"))
        assertEquals(listOf(TicketAction.OPEN_TERMINAL, TicketAction.FINISH), actions("in-progress"))
        assertEquals(listOf(TicketAction.OPEN_TERMINAL), actions("review"))
        assertEquals(emptyList(), actions("done"))
        assertEquals(emptyList(), actions("wontfix"))
        assertEquals(emptyList(), actions("paused"))
    }

    @Test
    fun problemsOfTheTicketAreListed() {
        val snapshot = snapshotOf(
            ticket("broken"), ticket("fine"),
            diagnostics = listOf(problem("broken", "enum", line = 5), problem("broken", "id-created-twice", Severity.WARNING, line = null)),
        )
        assertEquals(
            listOf(
                TicketProblem(5, "enum", "message of enum", error = true),
                TicketProblem(null, "id-created-twice", "message of id-created-twice", error = false),
            ),
            ticketViewState("broken", snapshot).problems,
        )
        assertEquals(emptyList(), ticketViewState("fine", snapshot).problems)
    }
}
