package dev.mateuy.safanoria.gui.ticket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mateuy.safanoria.core.Severity
import dev.mateuy.safanoria.core.Status
import dev.mateuy.safanoria.core.Ticket
import dev.mateuy.safanoria.core.TicketGraph
import dev.mateuy.safanoria.core.text
import dev.mateuy.safanoria.gui.data.FinishOutcome
import dev.mateuy.safanoria.gui.data.MergeOutcome
import dev.mateuy.safanoria.gui.data.ReopenOutcome
import dev.mateuy.safanoria.gui.data.StartOutcome
import dev.mateuy.safanoria.gui.data.Terminal
import dev.mateuy.safanoria.gui.data.TicketStore
import dev.mateuy.safanoria.gui.data.TicketsSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okio.Path

class TicketViewModel(private val id: String, private val store: TicketStore, private val terminal: Terminal) : ViewModel() {
    /** The running action and the last one's result: what the tickets themselves don't say. */
    private data class Activity(val busy: Boolean = false, val notice: Notice? = null, val confirming: TicketAction? = null)

    private val activity = MutableStateFlow(Activity())

    val state: StateFlow<TicketViewState> = combine(store.snapshot, activity) { snapshot, activity ->
        ticketViewState(id, snapshot).copy(busy = activity.busy, notice = activity.notice, confirming = activity.confirming)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ticketViewState(id, store.snapshot.value))

    /** The user asked for [action]: it runs now, or once confirmed ([confirm]) when it changes the repository. */
    fun request(action: TicketAction) {
        if (activity.value.busy) return
        // Said before starting anything, rather than by a terminal that fails once the ticket is started.
        if (action == TicketAction.START_IN_CLAUDE && !terminal.has(CLAUDE)) {
            activity.value = Activity(notice = Notice("Claude Code isn't installed: no `$CLAUDE` command found. Start opens a plain terminal.", error = true))
            return
        }
        if (action.confirmed) activity.update { it.copy(confirming = action) } else run(action)
    }

    /** Runs the action that was waiting to be confirmed. [input] is what its question asked for: the reason to reopen. */
    fun confirm(input: String = "") {
        activity.value.confirming?.let { run(it, input) }
    }

    fun cancel() {
        activity.update { it.copy(confirming = null) }
    }

    private fun run(action: TicketAction, input: String = "") = when (action) {
        TicketAction.START -> start()
        TicketAction.START_IN_CLAUDE -> start(
            // Named after the ticket: Claude Code shows the session's name as the terminal's title.
            command = listOf(CLAUDE, "--name", id, "Start working on ticket $id"),
        )
        TicketAction.OPEN_TERMINAL -> openTerminal()
        TicketAction.FINISH -> finish()
        TicketAction.MERGE -> merge()
        TicketAction.REOPEN -> reopen(input)
    }

    /** Starts the ticket and opens a terminal where it is to be worked on, running [command] when given. */
    private fun start(command: List<String> = emptyList()) = act {
        when (val outcome = store.start(id)) {
            is StartOutcome.NotStarted -> Notice("Not started: ${outcome.reason}", error = true)
            is StartOutcome.Started -> when (val workspace = outcome.workspace) {
                null -> Notice("Started, but ${outcome.note}", error = true)
                else -> openTerminal(workspace, "Started in $workspace", command)
            }
        }
    }

    /** Opens a terminal where the ticket's branch is checked out. */
    private fun openTerminal() = act {
        when (val workspace = store.workspace(id)) {
            null -> Notice("Branch '$id' isn't checked out anywhere: add a worktree for it (git worktree add).", error = true)
            else -> openTerminal(workspace, done = null)
        }
    }

    /** The work is complete: sets the ticket to review. */
    private fun finish() = act {
        when (val outcome = store.finish(id)) {
            is FinishOutcome.NotFinished -> Notice("Not finished: ${outcome.reason}", error = true)
            is FinishOutcome.Finished -> Notice(
                (listOf("In review, committed on ${outcome.branch}") + outcome.open.map { "Still open: $it" }).joinToString("\n"),
                error = false,
            )
        }
    }

    /** The review is fine: merges the ticket's branch into its target, done. */
    private fun merge() = act {
        when (val outcome = store.merge(id)) {
            is MergeOutcome.NotMerged -> Notice("Not merged: ${outcome.reason}", error = true)
            is MergeOutcome.Merged -> Notice((listOf("Merged into ${outcome.target} and done; nothing was pushed") + outcome.cleanUp).joinToString("\n"), error = false)
        }
    }

    /** The review found something: back to in progress, with [reason] logged. */
    private fun reopen(reason: String) = act {
        when (val outcome = store.reopen(id, reason)) {
            is ReopenOutcome.NotReopened -> Notice("Not reopened: ${outcome.reason}", error = true)
            is ReopenOutcome.Reopened -> Notice("In progress again, committed on ${outcome.branch}", error = false)
        }
    }

    private fun openTerminal(workspace: Path, done: String?, command: List<String> = emptyList()): Notice? =
        when (val problem = terminal.open(workspace, command)) {
            null -> done?.let { Notice(it, error = false) }
            else -> Notice(listOfNotNull(done, "No terminal opened: $problem").joinToString(". "), error = true)
        }

    /** Runs one action at a time; its result replaces the previous notice. */
    private fun act(action: suspend () -> Notice?) {
        if (activity.value.busy) return
        activity.value = Activity(busy = true)
        viewModelScope.launch { activity.value = Activity(notice = action()) }
    }

    private companion object {
        const val CLAUDE = "claude"
    }
}

internal fun ticketViewState(id: String, snapshot: TicketsSnapshot): TicketViewState {
    val graph = snapshot.graph ?: return TicketViewState(id, loading = true)
    val ticket = graph.ticket(id) ?: return TicketViewState(id, loading = false, found = false)
    val f = ticket.frontmatter
    fun link(t: Ticket) = TicketLink(t.fileId, t.frontmatter?.title?.value, t.frontmatter?.status)
    fun link(otherId: String) = graph.ticket(otherId)?.let(::link) ?: TicketLink(otherId, null, null)
    return TicketViewState(
        id = id,
        loading = false,
        title = f?.title?.value ?: "(no title)",
        status = f?.status,
        facts = listOfNotNull(
            f?.type?.let { "type" to it.text },
            f?.priority?.let { "priority" to it.text },
            f?.size?.let { "size" to it.text },
            f?.area?.takeIf { it.isNotEmpty() }?.let { areas -> "area" to areas.joinToString(", ") { it.value } },
            f?.tags?.takeIf { it.isNotEmpty() }?.let { tags -> "tags" to tags.joinToString(", ") { it.value } },
            f?.assignee?.let { "assignee" to it.value },
            f?.created?.let { "created" to it.value },
            f?.updated?.let { "updated" to it.value },
            ticket.branch?.let { "read from" to it },
        ),
        parent = f?.parent?.value?.let(::link),
        children = graph.children(ticket).map(::link),
        blockedBy = f?.blockedBy.orEmpty().map { it.value }.distinct().map(::link),
        blocks = graph.blocks(ticket).map(::link),
        body = body(ticket),
        actions = when (f?.status) {
            Status.BACKLOG, Status.READY -> listOf(TicketAction.START, TicketAction.START_IN_CLAUDE)
            Status.IN_PROGRESS -> listOf(TicketAction.OPEN_TERMINAL, TicketAction.FINISH)
            Status.REVIEW -> listOf(TicketAction.OPEN_TERMINAL, TicketAction.MERGE, TicketAction.REOPEN)
            else -> emptyList()
        },
        problems = snapshot.diagnostics[id].orEmpty().map { TicketProblem(it.line, it.code, it.message, it.severity == Severity.ERROR) },
    )
}

/** The file's text after the frontmatter. */
private fun body(ticket: Ticket): String {
    val lines = ticket.text.split('\n')
    val close = lines.drop(1).indexOfFirst { it.trimEnd() == "---" }
    val start = if (lines.firstOrNull()?.trimEnd() == "---" && close >= 0) close + 2 else 0
    return lines.drop(start).joinToString("\n").trim()
}
