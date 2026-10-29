package dev.mateuy.safanoria.gui.ticket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mateuy.safanoria.gui.data.Terminal
import dev.mateuy.safanoria.gui.data.launch
import dev.mateuy.safanoria.views.DiffOutcome
import dev.mateuy.safanoria.views.Notice
import dev.mateuy.safanoria.views.StartOutcome
import dev.mateuy.safanoria.views.TicketAction
import dev.mateuy.safanoria.views.TicketStore
import dev.mateuy.safanoria.views.TicketViewState
import dev.mateuy.safanoria.views.notice
import dev.mateuy.safanoria.views.ticketViewState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        val agent = action.agent
        if (agent != null && !terminal.has(agent.option)) {
            activity.value = Activity(notice = Notice("${agent.label} isn't installed: no `${agent.option}` command found. Start opens a plain terminal.", error = true))
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
        TicketAction.START_IN_CLAUDE, TicketAction.START_IN_CODEX -> start(
            command = listOf(action.agent!!.option) + action.agent!!.arguments(id, prompt = null),
        )
        TicketAction.CHECK_OUT -> checkOut()
        TicketAction.OPEN_TERMINAL -> openTerminal()
        TicketAction.DIFF -> diff()
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

    /** Continues here a ticket started on another computer: its worktree, then a terminal there. */
    private fun checkOut() = act {
        when (val outcome = store.checkOut(id)) {
            is StartOutcome.NotStarted -> Notice("No worktree added: ${outcome.reason}", error = true)
            is StartOutcome.Started -> outcome.workspace?.let { openTerminal(it, "Worktree added in $it") }
        }
    }

    /** Opens a terminal where the ticket's branch is checked out. */
    private fun openTerminal() = act {
        when (val workspace = store.workspace(id)) {
            null -> Notice("Branch '$id' isn't checked out anywhere: add a worktree for it (git worktree add).", error = true)
            else -> openTerminal(workspace, done = null)
        }
    }

    /** Opens the ticket's changes against its target in git's diff tool, which stays open on its own. */
    private fun diff() = act {
        when (val outcome = store.diff(id)) {
            is DiffOutcome.NotShown -> Notice("No diff: ${outcome.reason}", error = true)
            // Without one git guesses, maybe a terminal tool (vimdiff) that would run unseen here.
            is DiffOutcome.Ready if !outcome.hasTool -> Notice("No diff tool set: set yours with git config --global diff.tool <tool> (README: Diff)", error = true)
            is DiffOutcome.Ready -> when (val problem = withContext(Dispatchers.IO) { launch(outcome.directory, outcome.command) }) {
                null -> Notice((listOf("Changes against ${outcome.target} opened in the diff tool") + outcome.warnings.map { "Warning: $it" }).joinToString("\n"), error = false)
                else -> Notice("No diff: $problem", error = true)
            }
        }
    }

    /** The work is complete: sets the ticket to review. */
    private fun finish() = act { store.finish(id).notice }

    /** The review is fine: merges the ticket's branch into its target, done. */
    private fun merge() = act { store.merge(id).notice }

    /** The review found something: back to in progress, with [reason] logged. */
    private fun reopen(reason: String) = act { store.reopen(id, reason).notice }

    private fun openTerminal(workspace: Path, done: String?, command: List<String> = emptyList()): Notice? =
        when (val problem = terminal.open(workspace, command)) {
            null -> done?.let { Notice(it, error = false) }
            else -> Notice(listOfNotNull(done, "No terminal opened${command.firstOrNull()?.let { " for $it" }.orEmpty()}: $problem").joinToString(". "), error = true)
        }

    /** Runs one action at a time; its result replaces the previous notice. */
    private fun act(action: suspend () -> Notice?) {
        if (activity.value.busy) return
        activity.value = Activity(busy = true)
        viewModelScope.launch {
            activity.value = Activity(notice = try { action() } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Notice(e.message ?: e.toString(), error = true)
            })
        }
    }
}
