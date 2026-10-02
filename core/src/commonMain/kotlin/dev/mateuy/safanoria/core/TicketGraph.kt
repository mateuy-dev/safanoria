package dev.mateuy.safanoria.core

/** Checked Plan items out of all of them (SPEC §8.1: a parent is done when all are checked). */
public data class Progress(val done: Int, val total: Int)

/**
 * The tickets and their relations (SPEC §8), with the reverse ones derived: the children of a
 * parent, the tickets a ticket blocks. What `list`, `board` and apps show. Tickets are identified
 * by their filename id; references to missing tickets are kept as ids (`validate` reports them).
 */
public class TicketGraph(tickets: List<Ticket>) {
    private val byId = tickets.associateBy { it.fileId }

    /** In board order: status (in-progress, review, ready, backlog, done, wontfix), priority (urgent first), id. */
    public val tickets: List<Ticket> = tickets.sortedWith(ORDER)

    public fun ticket(id: String): Ticket? = byId[id]

    private val childrenOf: Map<String, List<Ticket>> = this.tickets
        .filter { it.frontmatter?.parent?.value in byId }
        .groupBy { it.frontmatter!!.parent!!.value }
        .mapValues { (parentId, kids) ->
            // Plan order is the suggested order (§8.1); children missing from the Plan go last, by id.
            val planOrder = byId.getValue(parentId).body.checklist("Plan").mapNotNull { it.childId }
            kids.sortedWith(compareBy<Ticket> { planOrder.indexOf(it.fileId).let { i -> if (i < 0) Int.MAX_VALUE else i } }.thenBy { it.fileId })
        }

    private val blocksOf: Map<String, List<Ticket>> = this.tickets
        .flatMap { t -> t.frontmatter?.blockedBy.orEmpty().map { it.value to t }.distinct() }
        .groupBy({ it.first }, { it.second })

    /** The parent ticket, or null when there is none or it doesn't exist. */
    public fun parent(ticket: Ticket): Ticket? = ticket.frontmatter?.parent?.value?.let { byId[it] }

    /** Tickets naming [ticket] as `parent`, in the parent's Plan order. */
    public fun children(ticket: Ticket): List<Ticket> = childrenOf[ticket.fileId].orEmpty()

    /** Tickets with [ticket] in `blockedBy`, in board order. */
    public fun blocks(ticket: Ticket): List<Ticket> = blocksOf[ticket.fileId].orEmpty()

    /** `blockedBy` ids that are not `done` (§8.2), including ids with no ticket. */
    public fun openBlockers(ticket: Ticket): List<String> =
        ticket.frontmatter?.blockedBy.orEmpty().map { it.value }.distinct()
            .filter { byId[it]?.frontmatter?.status != Status.DONE }

    /** Checked Plan items out of all, children and own steps; null when the Plan has no items. */
    public fun progress(ticket: Ticket): Progress? {
        val items = ticket.body.checklist("Plan")
        return if (items.isEmpty()) null else Progress(items.count { it.checked }, items.size)
    }

    public companion object {
        /** Status order on boards: work in hand first, closed last. */
        public val STATUS_ORDER: List<Status> =
            listOf(Status.IN_PROGRESS, Status.REVIEW, Status.READY, Status.BACKLOG, Status.DONE, Status.WONTFIX)

        private val ORDER: Comparator<Ticket> = compareBy<Ticket>(
            { t -> t.frontmatter?.status?.let { STATUS_ORDER.indexOf(it) } ?: STATUS_ORDER.size },
            { t -> t.frontmatter?.priority?.let { -it.ordinal } ?: 1 },
            { t -> t.fileId },
        )
    }
}
