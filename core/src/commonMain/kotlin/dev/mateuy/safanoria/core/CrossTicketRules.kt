package dev.mateuy.safanoria.core

/**
 * SPEC §12 rules that need several tickets: ids, references and the parent/child structure.
 * [otherIds] are ids of tickets on other branches: references to them are not unknown (§14).
 */
internal class CrossTicketRules(private val tickets: List<Ticket>, private val otherIds: Set<String> = emptySet()) {
    private val byId = tickets.associateBy { it.fileId }

    private fun known(id: String) = id in byId || id in otherIds
    private val out = mutableListOf<Finding>()

    private fun report(ticket: Ticket, line: Int?, code: String, message: String, causes: Collection<Ticket> = emptyList(), column: Int? = null) {
        out += Finding(Diagnostic(ticket.path, line, column, code, message), causes.map { it.path }.toSet())
    }

    private val Ticket.idLine: Int get() = frontmatter?.id?.line ?: 1

    fun findings(): List<Finding> {
        duplicateIds()
        references()
        blockedByCycles()
        parents()
        return out
    }

    /** The same frontmatter id in several files (each also has `id-mismatch` but one). */
    private fun duplicateIds() {
        tickets.groupBy { it.frontmatter?.id?.value }.filterKeys { it != null }.values.filter { it.size > 1 }.forEach { group ->
            for (t in group) report(t, t.idLine, "id-duplicate", "id '${t.frontmatter?.id?.value}' is also used by ${(group - t).joinToString { it.path.name }}", group - t)
        }
    }

    /** `parent`, `blockedBy`, `related`, Plan child items and Learnings `new ticket` must name existing tickets. */
    private fun references() {
        for (t in tickets) {
            val f = t.frontmatter ?: continue
            fun check(ref: Located<String>, field: String) {
                if (!known(ref.value)) report(t, ref.line, "ref-unknown", "$field: no ticket '${ref.value}'", column = ref.column)
            }
            f.parent?.let { check(it, "parent") }
            f.blockedBy.forEach { check(it, "blockedBy") }
            f.related.forEach { check(it, "related") }
            for (item in t.body.checklist("Plan")) {
                val child = item.childId ?: continue
                if (!known(child)) report(t, item.line, "ref-unknown", "Plan item names no ticket '$child'")
            }
            for (l in t.body.learnings) {
                val r = l.resolution
                if (r is Resolution.NewTicket && r.id.isNotEmpty() && !known(r.id)) {
                    report(t, l.resolutionLine, "ref-unknown", "Learning → new ticket: no ticket '${r.id}'")
                }
            }
        }
    }

    /** `blockedBy` must not form cycles (§8.2). Each cycle is reported on every ticket in it. */
    private fun blockedByCycles() {
        val edges = tickets.associate { t -> t.fileId to (t.frontmatter?.blockedBy?.map { it.value }?.filter { it in byId } ?: emptyList()) }
        val reported = mutableSetOf<Set<String>>()
        val state = mutableMapOf<String, Int>() // 1 = on the stack, 2 = done
        val stack = mutableListOf<String>()
        fun visit(id: String) {
            state[id] = 1
            stack += id
            for (next in edges[id].orEmpty()) {
                when (state[next]) {
                    null -> visit(next)
                    1 -> {
                        val cycle = stack.subList(stack.indexOf(next), stack.size).toList()
                        if (reported.add(cycle.toSet())) {
                            val path = (cycle + next).joinToString(" → ")
                            val members = cycle.map { byId.getValue(it) }
                            for ((i, member) in cycle.withIndex()) {
                                val target = cycle[(i + 1) % cycle.size]
                                val line = byId.getValue(member).frontmatter?.blockedBy?.firstOrNull { it.value == target }?.line
                                report(byId.getValue(member), line, "blocked-by-cycle", "blockedBy cycle: $path (§8.2)", members - byId.getValue(member))
                            }
                        }
                    }
                }
            }
            stack.removeAt(stack.lastIndex)
            state[id] = 2
        }
        tickets.map { it.fileId }.sorted().forEach { if (state[it] == null) visit(it) }
    }

    /** One level of parents, no research parents, Plans listing children, check state, versions (§8.1, §9). */
    private fun parents() {
        val children = tickets.filter { it.frontmatter?.parent?.value in byId }.groupBy { it.frontmatter!!.parent!!.value }
        for ((parentId, kids) in children) {
            val parent = byId.getValue(parentId)
            val pf = parent.frontmatter ?: continue

            pf.parent?.let { report(parent, it.line, "parent-nested", "'$parentId' has children, so it must not have a parent (one level only, §8.1)", kids) }
            if (pf.type == TicketType.RESEARCH) {
                report(parent, parent.idLine, "research-parent", "research tickets must not have children: ${kids.joinToString { it.fileId }} (§6.2)", kids)
            }

            val items = parent.body.checklist("Plan").filter { it.childId != null }
            val planLine = parent.body.section("Plan")?.headingLine ?: parent.idLine
            for (kid in kids) {
                val listed = items.filter { it.childId == kid.fileId }
                if (listed.isEmpty()) {
                    report(parent, planLine, "parent-plan-missing-child", "child '${kid.fileId}' must be listed in the Plan as - [ ] `${kid.fileId}`: … (§7.5)", listOf(kid))
                }
                listed.drop(1).forEach { report(parent, it.line, "parent-plan-duplicate-child", "child '${kid.fileId}' is listed more than once in the Plan", listOf(kid)) }
                listed.firstOrNull()?.let { item ->
                    val closed = kid.frontmatter?.status.let { it == Status.DONE || it == Status.WONTFIX }
                    if (item.checked != closed) {
                        val expected = if (closed) "checked" else "unchecked"
                        report(parent, item.line, "child-check-mismatch",
                            "Plan item for '${kid.fileId}' must be $expected: the child is ${kid.frontmatter?.status?.text} (§7.5)", listOf(kid))
                    }
                }
                childVersions(parent, kid)
            }
        }
        // Plan items naming an existing ticket that isn't a child of this one.
        for (t in tickets) for (item in t.body.checklist("Plan")) {
            val named = item.childId?.let { byId[it] } ?: continue
            if (named.frontmatter?.parent?.value != t.fileId) {
                report(t, item.line, "plan-item-not-child", "Plan item names '${named.fileId}', whose parent is ${named.frontmatter?.parent?.value ?: "not set"}; set its parent to '${t.fileId}' or remove the backticks", listOf(named))
            }
        }
    }

    /** A child's version must not be later than its parent's (§9). */
    private fun childVersions(parent: Ticket, kid: Ticket) {
        val parentVersions = parent.frontmatter?.resolvedIn ?: return
        for ((component, version) in kid.frontmatter?.resolvedIn ?: return) {
            val childV = version.value?.let(Version::parse) ?: continue
            val parentV = parentVersions[component]?.value?.let(Version::parse) ?: continue
            if (childV > parentV) {
                report(kid, version.line, "child-resolved-later",
                    "resolvedIn.$component ${version.value} is later than parent '${parent.fileId}' (${parentVersions[component]?.value}) (§9)", listOf(parent), version.column)
            }
        }
    }
}
