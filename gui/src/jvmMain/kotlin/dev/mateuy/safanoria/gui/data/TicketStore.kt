package dev.mateuy.safanoria.gui.data

import dev.mateuy.safanoria.core.Branches
import dev.mateuy.safanoria.core.Repository
import dev.mateuy.safanoria.core.TicketGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Path

/** The project's tickets as last read, and whether a read is running or failed. */
data class TicketsSnapshot(
    val graph: TicketGraph? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * The single source of the project's tickets for every screen: reads them through `core` (each
 * ticket's real copy from every local branch, SPEC §14, as `safanoria list` does) and keeps the
 * last result. Operations that change tickets (start, finish) belong here too, followed by a
 * [refresh].
 */
class TicketStore(val root: Path) {
    private val state = MutableStateFlow(TicketsSnapshot())
    val snapshot: StateFlow<TicketsSnapshot> = state.asStateFlow()

    private val reading = Mutex()

    /** Reads the tickets again. The previous ones stay visible until the new ones are ready. */
    suspend fun refresh() = reading.withLock {
        state.update { it.copy(loading = true, error = null) }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                // A Repository reads its files once, so a fresh one sees the current files.
                val repository = Repository(root)
                (Branches.read(repository)?.graph ?: repository.graph).also { graph ->
                    // Parse now, off the UI thread: tickets parse lazily.
                    graph.tickets.forEach { it.frontmatter; it.body }
                }
            }
        }
        state.update {
            result.fold(
                onSuccess = { graph -> TicketsSnapshot(graph) },
                onFailure = { e -> it.copy(loading = false, error = e.message ?: e.toString()) },
            )
        }
    }
}
