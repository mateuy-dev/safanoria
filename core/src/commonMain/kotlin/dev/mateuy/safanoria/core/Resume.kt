package dev.mateuy.safanoria.core

import okio.Path

/**
 * Where work on [ticket] is. [branch] is `<id>` when that branch exists. [worktree] is the
 * repository root in the worktree that has it checked out, and [uncommitted] the number of
 * changed files there (null without a worktree).
 */
public data class ResumePoint(
    val ticket: Ticket,
    val branch: String?,
    val worktree: Path?,
    val uncommitted: Int?,
    /** The first unchecked Plan item. */
    val next: CheckItem?,
    val lastLog: WorkLogEntry?,
)

/**
 * Finds where to continue work from the tickets and git alone, not from an agent's session: a
 * fresh session in any checkout gets the same answer. [graph] should hold each ticket's real
 * copy (SPEC §14), so a started ticket has its branch's status.
 */
public class Resume(private val repository: Repository, private val graph: TicketGraph) {
    private val git = repository.git

    private val branches: Set<String> by lazy { runCatching { git.branches().toSet() }.getOrDefault(emptySet()) }

    /** The repository root in every worktree, by the branch checked out there. */
    private val worktrees: Map<String, Path> by lazy {
        val all = try { git.worktrees() } catch (e: GitException) { return@lazy emptyMap() }
        val layout = Branches.layout(repository, all) ?: return@lazy emptyMap()
        layout.others + listOfNotNull(layout.here.branch?.let { it to repository.root })
    }

    /**
     * The tickets to continue:
     * - no [query]: every `in-progress` ticket;
     * - a ticket id: that ticket;
     * - else words: the `in-progress` and `review` tickets whose id or title contains them all.
     * A parent is replaced by its `in-progress` children when it has some, so naming the parent
     * finds a child the user didn't know was started.
     */
    public fun candidates(query: String? = null): List<Ticket> {
        val q = query?.trim().orEmpty()
        val found = when {
            q.isEmpty() -> graph.tickets.filter { it.status == Status.IN_PROGRESS }
            graph.ticket(q) != null -> listOf(graph.ticket(q)!!)
            else -> {
                val words = q.lowercase().split(Regex("\\s+"))
                graph.tickets.filter { t ->
                    val text = "${t.fileId} ${t.frontmatter?.title?.value.orEmpty()}".lowercase()
                    t.status in ACTIVE && words.all { it in text }
                }
            }
        }
        return found.flatMap { t -> graph.children(t).filter { it.status == Status.IN_PROGRESS }.ifEmpty { listOf(t) } }.distinct()
    }

    public fun point(ticket: Ticket): ResumePoint {
        val id = ticket.fileId
        val worktree = worktrees[id]
        val uncommitted = worktree?.let { path ->
            runCatching { Git(path).run("status", "--porcelain").lines().count { it.isNotBlank() } }.getOrNull()
        }
        return ResumePoint(
            ticket = ticket,
            branch = id.takeIf { it in branches },
            worktree = worktree,
            uncommitted = uncommitted,
            next = ticket.body.checklist("Plan").firstOrNull { !it.checked },
            lastLog = ticket.body.workLog.lastOrNull(),
        )
    }

    private val Ticket.status: Status? get() = frontmatter?.status

    private companion object {
        val ACTIVE = setOf(Status.IN_PROGRESS, Status.REVIEW)
    }
}
