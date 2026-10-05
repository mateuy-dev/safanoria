package dev.mateuy.safanoria.gui

import dev.mateuy.safanoria.core.Diagnostic
import dev.mateuy.safanoria.core.Progress
import dev.mateuy.safanoria.core.Severity
import dev.mateuy.safanoria.core.Status
import dev.mateuy.safanoria.core.TicketFilter
import dev.mateuy.safanoria.core.TicketType
import dev.mateuy.safanoria.gui.board.BoardViewState
import dev.mateuy.safanoria.gui.board.boardViewState
import dev.mateuy.safanoria.gui.data.TicketsSnapshot
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoardViewStateTest {
    private fun BoardViewState.ids(status: Status?) = columns.single { it.status == status }.cards.map { it.id }
    private fun BoardViewState.card(id: String) = columns.flatMap { it.cards }.single { it.id == id }

    @Test
    fun loadingUntilTheFirstRead() {
        assertTrue(boardViewState(TicketsSnapshot()).loading)
        assertTrue(boardViewState(TicketsSnapshot(loading = true)).loading)
    }

    @Test
    fun aFailedFirstReadShowsTheErrorAndStopsLoading() {
        val state = boardViewState(TicketsSnapshot(error = "not a git repository"))
        assertFalse(state.loading)
        assertEquals("not a git repository", state.error)
    }

    @Test
    fun everyStatusHasAColumnInWorkflowOrder() {
        val state = boardViewState(snapshotOf(ticket("one-ticket", status = "ready")))
        assertEquals(
            listOf(Status.BACKLOG, Status.READY, Status.IN_PROGRESS, Status.REVIEW, Status.DONE, Status.WONTFIX),
            state.columns.map { it.status },
        )
        assertEquals(listOf("one-ticket"), state.ids(Status.READY))
        assertEquals(1, state.ticketCount)
    }

    @Test
    fun cardsAreInPriorityThenIdOrder() {
        val state = boardViewState(snapshotOf(ticket("bbb"), ticket("aaa"), ticket("zzz", priority = "urgent")))
        assertEquals(listOf("zzz", "aaa", "bbb"), state.ids(Status.BACKLOG))
    }

    @Test
    fun anUnreadableStatusGetsItsOwnLastColumn() {
        val state = boardViewState(snapshotOf(ticket("odd-one", status = "paused")))
        assertNull(state.columns.last().status)
        assertEquals(listOf("odd-one"), state.ids(null))
    }

    @Test
    fun childrenAreTheirOwnCardsAndParentsShowProgress() {
        val state = boardViewState(
            snapshotOf(
                ticket("parent-one", status = "in-progress", plan = "- [x] `child-done`: first\n- [ ] `child-open`: second"),
                ticket("child-done", status = "done", extra = "parent: parent-one"),
                ticket("child-open", extra = "parent: parent-one"),
            ),
        )
        assertEquals(Progress(1, 2), state.card("parent-one").progress)
        assertEquals("parent-one", state.card("child-open").parentId)
        assertEquals(listOf("child-done"), state.ids(Status.DONE))
        assertNull(state.card("child-open").progress)
    }

    @Test
    fun blockersThatAreNotDoneAreShown() {
        val state = boardViewState(
            snapshotOf(
                ticket("waiting", extra = "blockedBy: [open-one, done-one]"),
                ticket("open-one"),
                ticket("done-one", status = "done"),
            ),
        )
        assertEquals(listOf("open-one"), state.card("waiting").openBlockers)
    }

    @Test
    fun aTicketOnlyOnABranchSaysSo() {
        val state = boardViewState(snapshotOf(ticket("draft", branch = "other", onlyOnBranch = true), ticket("usual", branch = "main")))
        assertEquals("other", state.card("draft").onlyOnBranch)
        assertNull(state.card("usual").onlyOnBranch)
    }

    @Test
    fun collapsedColumnsKeepTheirCards() {
        val state = boardViewState(snapshotOf(ticket("closed", status = "done")), collapsed = setOf(Status.DONE))
        assertEquals(listOf(Status.DONE), state.columns.filter { it.collapsed }.map { it.status })
        assertEquals(listOf("closed"), state.ids(Status.DONE))
    }

    @Test
    fun filtersNarrowTheCardsButNotTheCounts() {
        val snapshot = snapshotOf(
            ticket("a-bug", type = "bug", extra = "area: [web]"),
            ticket("a-feature", extra = "area: [app, web]"),
            ticket("blocked-bug", type = "bug", extra = "blockedBy: [a-bug]"),
        )
        fun shown(filter: TicketFilter) = boardViewState(snapshot, filter = filter).let { state -> state.columns.flatMap { it.cards }.map { it.id } }

        assertEquals(listOf("a-bug", "blocked-bug"), shown(TicketFilter(types = setOf(TicketType.BUG))))
        assertEquals(listOf("a-bug", "a-feature"), shown(TicketFilter(areas = setOf("web"))))
        assertEquals(listOf("a-feature"), shown(TicketFilter(areas = setOf("app"))))
        assertEquals(listOf("blocked-bug"), shown(TicketFilter(blocked = true)))
        assertEquals(emptyList(), shown(TicketFilter(types = setOf(TicketType.RESEARCH))))

        val state = boardViewState(snapshot, filter = TicketFilter(blocked = true))
        assertEquals(3, state.ticketCount)
        assertEquals(1, state.shownCount)
        assertEquals(listOf("app", "web"), state.areas)
        assertEquals(6, state.columns.size)
    }

    @Test
    fun problemsAreCountedOnTheirCard() {
        val state = boardViewState(
            snapshotOf(
                ticket("broken"), ticket("fine"),
                diagnostics = listOf(problem("broken", "enum"), problem("broken", "date"), problem("broken", "id-created-twice", Severity.WARNING)),
            ),
        )
        assertEquals(2 to 1, state.card("broken").let { it.errors to it.warnings })
        assertEquals(0 to 0, state.card("fine").let { it.errors to it.warnings })
    }

    @Test
    fun problemsOutsideTicketsAreProjectProblems() {
        val config = Diagnostic("/project/safanoria.yaml".toPath(), 2, 1, "config-schema", "dir must be a string")
        val state = boardViewState(snapshotOf(ticket("fine"), diagnostics = listOf(config)))
        assertEquals(listOf("/project/safanoria.yaml:2:1: error[config-schema]: dir must be a string"), state.projectProblems)
        assertEquals(0, state.card("fine").errors)
    }

    @Test
    fun problemsOfTheCheckoutAreNotShownOnACopyFromAnotherBranch() {
        // validate checked this checkout's (main's) copy; the board shows the one from the ticket's branch.
        val state = boardViewState(
            snapshotOf(
                ticket("started", branch = "started"), ticket("here", branch = "main"),
                diagnostics = listOf(problem("started", "section-empty"), problem("here", "enum")),
                checkoutBranch = "main",
            ),
        )
        assertEquals(0, state.card("started").errors)
        assertEquals(1, state.card("here").errors)
    }
}
