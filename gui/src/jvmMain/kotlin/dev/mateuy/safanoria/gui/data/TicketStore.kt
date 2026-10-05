package dev.mateuy.safanoria.gui.data

import dev.mateuy.safanoria.core.BranchView
import dev.mateuy.safanoria.core.Branches
import dev.mateuy.safanoria.core.Diagnostic
import dev.mateuy.safanoria.core.Finish
import dev.mateuy.safanoria.core.FinishException
import dev.mateuy.safanoria.core.FinishResult
import dev.mateuy.safanoria.core.GitException
import dev.mateuy.safanoria.core.Land
import dev.mateuy.safanoria.core.LandException
import dev.mateuy.safanoria.core.LandResult
import dev.mateuy.safanoria.core.Reopen
import dev.mateuy.safanoria.core.ReopenResult
import dev.mateuy.safanoria.core.Repository
import dev.mateuy.safanoria.core.Start
import dev.mateuy.safanoria.core.StartException
import dev.mateuy.safanoria.core.StartResult
import dev.mateuy.safanoria.core.Status
import dev.mateuy.safanoria.core.TicketGraph
import dev.mateuy.safanoria.core.Validator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Path
import java.time.LocalDate

/** What [TicketStore.start] did. */
sealed interface StartOutcome {
    /** Started. [workspace] is where to work on it, or null when there is none yet, and [note] says why. */
    data class Started(val workspace: Path?, val note: String? = null) : StartOutcome

    data class NotStarted(val reason: String) : StartOutcome
}

/** What [TicketStore.finish] did. */
sealed interface FinishOutcome {
    /** In review, committed on [branch]. [open] is what the ticket still has open: unchecked criteria, pending Learnings. */
    data class Finished(val branch: String, val open: List<String> = emptyList()) : FinishOutcome

    data class NotFinished(val reason: String) : FinishOutcome
}

/** What [TicketStore.merge] did. */
sealed interface MergeOutcome {
    /** Merged into [target] and done. [cleanUp] says what happened to the worktree and the branch. */
    data class Merged(val target: String, val cleanUp: List<String>) : MergeOutcome

    data class NotMerged(val reason: String) : MergeOutcome
}

/** What [TicketStore.reopen] did. */
sealed interface ReopenOutcome {
    data class Reopened(val branch: String) : ReopenOutcome

    data class NotReopened(val reason: String) : ReopenOutcome
}

/** The project's tickets as last read, and whether a read is running or failed. */
data class TicketsSnapshot(
    val graph: TicketGraph? = null,
    /** `validate`'s problems by ticket id, for the tickets shown as they are in this checkout. */
    val diagnostics: Map<String, List<Diagnostic>> = emptyMap(),
    /** Problems that belong to no shown ticket: `safanoria.yaml`, attachments. */
    val projectDiagnostics: List<Diagnostic> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * The single source of the project's tickets for every screen: reads them through `core` (each
 * ticket's real copy from every local branch, SPEC §14, as `safanoria-cli list` does) and keeps the
 * last result. Operations that change tickets ([start], [finish], [merge], [reopen]) are here too, followed by
 * a [refresh].
 */
class TicketStore(val root: Path) {
    private val state = MutableStateFlow(TicketsSnapshot())
    val snapshot: StateFlow<TicketsSnapshot> = state.asStateFlow()

    private val reading = Mutex()

    /**
     * Starts ticket [id] as `safanoria-cli start` does (SPEC §11.2): branch `<id>` with the ticket
     * `in-progress` on it, then the worktree when `safanoria.yaml` has one, else this checkout
     * switched to the branch. The tickets are read again afterwards.
     */
    suspend fun start(id: String, today: String = LocalDate.now().toString()): StartOutcome {
        val outcome = withContext(Dispatchers.IO) {
            try {
                val repository = Repository(root)
                val branches = Branches.read(repository)
                    ?: return@withContext StartOutcome.NotStarted("needs a git repository with the branch '${repository.config.mainBranch}' (mainBranch)")
                val ready = when (val r = Start.prepare(repository, branches, id)) {
                    is StartResult.Refused -> return@withContext StartOutcome.NotStarted(r.reason)
                    is StartResult.Ready -> r
                }
                try {
                    Start.begin(repository, ready, today)
                } catch (e: StartException) {
                    return@withContext StartOutcome.NotStarted((listOf(e.message) + e.problems.map { "${it.code}: ${it.message}" }).joinToString("\n"))
                }
                if (ready.worktree != null) {
                    try {
                        Start.addWorktree(repository, ready)
                        StartOutcome.Started(ready.worktree)
                    } catch (e: GitException) {
                        StartOutcome.Started(null, "the worktree wasn't added: ${e.message}\nAdd it with: git worktree add ${ready.worktree} $id")
                    }
                } else {
                    val note = Start.switchCheckout(repository, id)
                    StartOutcome.Started(root.takeIf { note == null }, note)
                }
            } catch (e: Exception) {
                StartOutcome.NotStarted(e.message ?: e.toString())
            }
        }
        if (outcome is StartOutcome.Started) refresh()
        return outcome
    }

    /**
     * Sets ticket [id] to `review` as `safanoria-cli finish` does (SPEC §11.4): on its real copy, in
     * one commit with only that file, unless its branch has uncommitted changes or is behind its
     * target. The tickets are read again afterwards.
     */
    suspend fun finish(id: String, today: String = LocalDate.now().toString()): FinishOutcome {
        val outcome = withContext(Dispatchers.IO) {
            try {
                val repository = Repository(root)
                val branches = Branches.read(repository)
                    ?: return@withContext FinishOutcome.NotFinished("needs a git repository with the branch '${repository.config.mainBranch}' (mainBranch)")
                val ready = when (val r = Finish.prepare(repository, branches, id, Status.REVIEW)) {
                    is FinishResult.Refused -> return@withContext FinishOutcome.NotFinished(r.reason)
                    is FinishResult.Ready -> r
                }
                Finish.perform(repository, ready, today)
                FinishOutcome.Finished(ready.branch, ready.open)
            } catch (e: FinishException) {
                FinishOutcome.NotFinished((listOf(e.message) + e.problems.map { "${it.code}: ${it.message}" }).joinToString("\n"))
            } catch (e: Exception) {
                FinishOutcome.NotFinished(e.message ?: e.toString())
            }
        }
        if (outcome is FinishOutcome.Finished) refresh()
        return outcome
    }

    /**
     * Lands ticket [id], in review, as `safanoria-cli merge` does (SPEC §11.5): one merge commit on
     * its target with the ticket `done` in it, then its worktree and branch go. Nothing is pushed.
     */
    suspend fun merge(id: String, today: String = LocalDate.now().toString()): MergeOutcome {
        val outcome = withContext(Dispatchers.IO) {
            try {
                val repository = Repository(root)
                val branches = Branches.read(repository)
                    ?: return@withContext MergeOutcome.NotMerged("needs a git repository with the branch '${repository.config.mainBranch}' (mainBranch)")
                val ready = when (val r = Land.prepare(repository, branches, id)) {
                    is LandResult.Refused -> return@withContext MergeOutcome.NotMerged(r.reason)
                    is LandResult.Ready -> r
                }
                val landed = Land.perform(repository, ready, today)
                MergeOutcome.Merged(landed.target, landed.cleanUp)
            } catch (e: LandException) {
                MergeOutcome.NotMerged((listOf(e.message) + e.problems.map { "${it.code}: ${it.message}" }).joinToString("\n"))
            } catch (e: Exception) {
                MergeOutcome.NotMerged(e.message ?: e.toString())
            }
        }
        if (outcome is MergeOutcome.Merged) refresh()
        return outcome
    }

    /** Sends ticket [id] back from `review` to `in-progress` as `safanoria-cli reopen` does, logging [reason] (SPEC §6.1). */
    suspend fun reopen(id: String, reason: String, today: String = LocalDate.now().toString()): ReopenOutcome {
        val outcome = withContext(Dispatchers.IO) {
            try {
                val repository = Repository(root)
                val branches = Branches.read(repository)
                    ?: return@withContext ReopenOutcome.NotReopened("needs a git repository with the branch '${repository.config.mainBranch}' (mainBranch)")
                val ready = when (val r = Reopen.prepare(branches, id, reason)) {
                    is ReopenResult.Refused -> return@withContext ReopenOutcome.NotReopened(r.reason)
                    is ReopenResult.Ready -> r
                }
                ReopenOutcome.Reopened(Reopen.perform(repository, ready, today).branch)
            } catch (e: FinishException) {
                ReopenOutcome.NotReopened((listOf(e.message) + e.problems.map { "${it.code}: ${it.message}" }).joinToString("\n"))
            } catch (e: Exception) {
                ReopenOutcome.NotReopened(e.message ?: e.toString())
            }
        }
        if (outcome is ReopenOutcome.Reopened) refresh()
        return outcome
    }

    /** The directory where ticket [id]'s branch is checked out (its worktree, or this checkout), or null. */
    suspend fun workspace(id: String): Path? = withContext(Dispatchers.IO) {
        runCatching { BranchView.open(Repository(root), id)?.worktree }.getOrNull()
    }

    /** Reads the tickets again. The previous ones stay visible until the new ones are ready. */
    suspend fun refresh() = reading.withLock {
        state.update { it.copy(loading = true, error = null) }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                // A Repository reads its files once, so a fresh one sees the current files.
                val repository = Repository(root)
                val branches = Branches.read(repository)
                val graph = branches?.graph ?: repository.graph
                // Parse now, off the UI thread: tickets parse lazily.
                graph.tickets.forEach { it.frontmatter; it.body }
                snapshot(graph, Validator(repository, branches).validate(), repository.ticketDir, branches?.branches?.firstOrNull())
            }
        }
        state.update {
            result.fold(
                onSuccess = { snapshot -> snapshot },
                onFailure = { e -> it.copy(loading = false, error = e.message ?: e.toString()) },
            )
        }
    }
}

/**
 * [graph] with [diagnostics] sorted out by ticket. `validate` checks this checkout's files
 * ([checkoutBranch]), while the graph may show a ticket's copy from another branch: a problem is
 * only attached to a ticket when the copy shown is the one checked, and dropped otherwise.
 */
internal fun snapshot(graph: TicketGraph, diagnostics: List<Diagnostic>, ticketDir: Path, checkoutBranch: String?): TicketsSnapshot {
    fun ticketId(d: Diagnostic): String? = d.file?.takeIf { it.parent == ticketDir && it.name.endsWith(".md") }?.name?.removeSuffix(".md")
    val (ofTickets, ofProject) = diagnostics.partition { d -> ticketId(d)?.let { graph.ticket(it) } != null }
    val shownAsChecked = graph.tickets.filter { it.branch == null || it.branch == checkoutBranch }.map { it.fileId }.toSet()
    return TicketsSnapshot(
        graph = graph,
        diagnostics = ofTickets.groupBy { ticketId(it)!! }.filterKeys { it in shownAsChecked },
        projectDiagnostics = ofProject,
    )
}
