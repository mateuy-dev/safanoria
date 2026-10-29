package dev.mateuy.safanoria.views

import dev.mateuy.safanoria.core.BoardGroup
import dev.mateuy.safanoria.core.Severity
import dev.mateuy.safanoria.core.Status
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
    fun aParentShowsItsChildrenAsABoard() {
        val snapshot = snapshotOf(
            ticket("parent-one", plan = "- [ ] `second`: later\n- [x] `first`: sooner\n- [ ] `third`: last"),
            ticket("first", status = "done", extra = "parent: parent-one"),
            ticket("second", extra = "parent: parent-one"),
            ticket("third", extra = "parent: parent-one"),
            ticket("stranger", status = "review"),
        )
        val board = ticketViewState("parent-one", snapshot).childrenBoard
        // The columns of the board, each with the children in that status, in the Plan's order.
        assertEquals(
            listOf(
                BoardGroup.BACKLOG, BoardGroup.READY, BoardGroup.IN_PROGRESS, BoardGroup.REVIEW,
                BoardGroup.TO_RELEASE, BoardGroup.RELEASED, BoardGroup.WONTFIX,
            ),
            board.map { it.group },
        )
        assertEquals(
            mapOf<BoardGroup?, List<String>>(BoardGroup.BACKLOG to listOf("second", "third"), BoardGroup.RELEASED to listOf("first")),
            board.filter { it.cards.isNotEmpty() }.associate { it.group to it.cards.map { c -> c.id } },
        )
        // Only the columns with children are open, and the cards don't repeat the parent.
        assertEquals(listOf(BoardGroup.BACKLOG, BoardGroup.RELEASED), board.filter { !it.collapsed }.map { it.group })
        assertTrue(board.flatMap { it.cards }.all { it.parentId == null })
    }

    @Test
    fun aTicketWithoutChildrenHasNoBoard() {
        val snapshot = snapshotOf(ticket("parent-one"), ticket("child", extra = "parent: parent-one"))
        assertEquals(emptyList(), ticketViewState("child", snapshot).childrenBoard)
    }

    @Test
    fun theActionsDependOnTheStatus() {
        fun actions(status: String) = ticketViewState("the-one", snapshotOf(ticket("the-one", status = status))).actions
        assertEquals(listOf(TicketAction.START, TicketAction.START_IN_CLAUDE, TicketAction.START_IN_CODEX), actions("backlog"))
        assertEquals(listOf(TicketAction.START, TicketAction.START_IN_CLAUDE, TicketAction.START_IN_CODEX), actions("ready"))
        assertEquals(listOf(TicketAction.OPEN_TERMINAL, TicketAction.DIFF, TicketAction.FINISH), actions("in-progress"))
        assertEquals(listOf(TicketAction.OPEN_TERMINAL, TicketAction.DIFF, TicketAction.MERGE, TicketAction.REOPEN), actions("review"))
        assertEquals(emptyList(), actions("done"))
        assertEquals(emptyList(), actions("wontfix"))
        assertEquals(emptyList(), actions("paused"))
        // Started on another computer: only its worktree can be created here, whatever its status, and its changes seen.
        val remote = ticketViewState("the-one", snapshotOf(ticket("the-one", status = "in-progress", branch = "the-one", remote = "origin")))
        assertEquals(listOf(TicketAction.CHECK_OUT, TicketAction.DIFF), remote.actions)
        assertTrue("read from" to "origin/the-one" in remote.facts)
    }

    @Test
    fun defaultAgentIsFirstAndExplicitActionsKeepTheirIdentity() {
        val snapshot = snapshotOf(ticket("the-one"))
        val fallback = ticketViewState("the-one", snapshot.copy(config = null))
        assertEquals(dev.mateuy.safanoria.core.SessionAgent.CLAUDE, fallback.defaultAgent)
        val codex = dev.mateuy.safanoria.core.SessionAgent.CODEX
        val state = ticketViewState("the-one", snapshot.copy(config = snapshot.config!!.copy(defaultAgent = codex)))
        assertEquals(codex, state.defaultAgent)
        assertEquals(listOf(TicketAction.START, TicketAction.START_IN_CODEX, TicketAction.START_IN_CLAUDE), state.actions)
        assertEquals("Start in Codex (default)", state.actions[1].agentLabel(state.defaultAgent))
        assertEquals(dev.mateuy.safanoria.core.SessionAgent.CLAUDE, state.actions[2].agent)
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
