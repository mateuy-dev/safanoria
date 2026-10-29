package dev.mateuy.safanoria.tui

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.markdown.Markdown
import com.github.ajalt.mordant.rendering.TextStyle
import dev.mateuy.safanoria.core.SessionAgent
import dev.mateuy.safanoria.core.Size
import dev.mateuy.safanoria.views.agentLabel
import dev.mateuy.safanoria.views.BoardColumn
import dev.mateuy.safanoria.views.DiffOutcome
import dev.mateuy.safanoria.views.Notice
import dev.mateuy.safanoria.views.StartOutcome
import dev.mateuy.safanoria.views.TicketAction
import dev.mateuy.safanoria.views.TicketLink
import dev.mateuy.safanoria.views.TicketViewState
import dev.mateuy.safanoria.views.notice
import dev.mateuy.safanoria.views.ticketViewState
import okio.Path

/**
 * A ticket: its facts, links, problems, children and body, and what can be done with it in its
 * status, as in the desktop app. Actions that change the repository are confirmed first.
 */
internal class TicketPage(private val nav: Navigator, private val id: String) : Page {
    private var state = TicketViewState(id)
    private var busy = false
    private var notice: Notice? = null
    private var confirming: TicketAction? = null
    private val reason = StringBuilder()

    private var scroll = 0

    /** What Tab goes through: the action buttons, then links and child cards, in screen order. */
    private class Focusable(val y: Int?, val height: Int, val activate: () -> Unit)

    private var focusables = listOf<Focusable>()
    private var focus = -1
    private var followFocus = false

    private var body: Pair<Pair<String, Int>, List<List<Cell>>>? = null

    override val typing: Boolean get() = confirming == TicketAction.REOPEN

    override fun draw(p: Painter) {
        state = ticketViewState(id, nav.store.snapshot.value)
        val found = mutableListOf<Focusable>()
        fun focused() = focus == found.size

        var x = p.button(1, 0, "← Back") { nav.back() } + 1
        x = p.text(x, 0, id, Theme.accent) + 1
        val badge = state.status?.label?.let { textWidth(it) + 2 } ?: 0
        p.text(x, 0, state.title, Theme.bold, max = p.width - x - badge - 2)
        if (!state.loading && state.found) p.statusBadge(p.width - 1 - badge, 0, state.status)

        // The actions: buttons with their keys.
        var y = 1
        x = 1
        for (action in state.actions) {
            val label = "${action.agentLabel(state.defaultAgent) ?: action.label} (${action.key})"
            if (x > 1 && x + buttonWidth(label) > p.width) { x = 1; y++ }
            val isFocused = focused()
            x = p.button(x, y, label, focused = isFocused, enabled = !busy) { request(action) } + 1
            found += Focusable(null, 1) { request(action) }
        }
        if (busy) p.text(x + 1, y, "working…", Theme.dim)
        if (state.actions.isNotEmpty() || busy) y++
        notice?.let { n -> wrap(n.text, p.width - 2).take(6).forEach { p.text(1, y++, it, if (n.error) Theme.error else Theme.success) } }
        p.rule(y++)

        val area = p.area(0, y, p.width, p.height - y - 1)
        when {
            state.loading -> area.text(1, 0, "Reading tickets…", Theme.dim)
            !state.found -> area.text(1, 0, "No ticket '$id' (any more).", Theme.error)
            else -> drawContent(area, found)
        }
        focusables = found
        followFocus = false

        val hints = listOf("↑↓ scroll", "tab/⏎ links", "esc back", "r refresh", "? keys") +
            state.actions.map { "${it.key} ${it.label.lowercase()}" }
        p.statusBar(hints.joinToString(" · "))
        confirming?.let { drawConfirmation(p, it) }
    }

    /** Everything under the actions, scrolled: facts, links, problems, children, body. */
    private fun drawContent(area: Painter, found: MutableList<Focusable>) {
        val width = area.width - 2
        val c = area.scrolled(scroll).area(1, 0, width, Int.MAX_VALUE / 2)
        var y = 0

        // Facts flow as "label value" pairs.
        var x = 0
        for ((label, value) in state.facts) {
            val w = textWidth(label) + textWidth(value) + 1
            if (x > 0 && x + 3 + w > width) { x = 0; y++ }
            if (x > 0) x = c.text(x, y, " · ", Theme.dim)
            x = c.runs(x, y, listOf(Run("$label ", Theme.dim), Run(value)), max = width - x)
        }
        y += 2

        fun links(label: String, links: List<TicketLink>) {
            if (links.isEmpty()) return
            c.text(0, y, label, Theme.dim)
            for (link in links) {
                val open = { nav.openTicket(link.id) }
                val style = if (focus == found.size) Theme.link + Theme.selected else Theme.link
                var lx = c.text(12, y, link.id, style)
                link.status?.let { lx = c.text(lx + 1, y, "●", TextStyle(Theme.status(it))) }
                c.text(lx + 1, y, link.title ?: "(missing)", if (link.title == null) Theme.error else Plain, max = width - lx - 1)
                c.region(12, y, width - 12, 1, click = open)
                found += Focusable(y, 1, open)
                y++
            }
        }
        links("Parent", listOfNotNull(state.parent))
        if (state.childrenBoard.isEmpty()) links("Children", state.children)
        links("Blocked by", state.blockedBy)
        links("Blocks", state.blocks)
        if (state.parent != null || state.blockedBy.isNotEmpty() || state.blocks.isNotEmpty() || (state.childrenBoard.isEmpty() && state.children.isNotEmpty())) y++

        if (state.problems.isNotEmpty()) {
            c.text(0, y++, "Problems (safanoria-cli validate)", Theme.bold)
            for (problem in state.problems) {
                val text = "⚠ " + (problem.line?.let { "line $it · " } ?: "") + "${problem.code}: ${problem.message}"
                wrap(text, width).forEach { c.text(0, y++, it, if (problem.error) Theme.error else Theme.warning) }
            }
            y++
        }

        if (state.childrenBoard.isNotEmpty()) {
            c.text(0, y++, "Children (${state.children.size})", Theme.bold)
            y = drawChildren(c, y, width, state.childrenBoard, found) + 1
        }

        for (line in bodyLines(width)) {
            line.forEachIndexed { i, cell -> c.set(i, y, cell) }
            y++
        }

        // Scrolling: by keys and wheel, and to the focused link.
        val total = y
        if (followFocus) found.getOrNull(focus)?.y?.let { fy ->
            val h = found[focus].height
            if (fy < scroll) scroll = fy
            if (fy + h > scroll + area.height) scroll = fy + h - area.height
        }
        val max = (total - area.height).coerceAtLeast(0)
        scroll = scroll.coerceIn(0, max)
        area.region(0, 0, area.width, area.height, scroll = { dy -> scroll = (scroll + dy * 3).coerceIn(0, max) })
        if (scroll > 0) area.text(area.width - 1, 0, "▲", Theme.dim)
        if (scroll < max) area.text(area.width - 1, area.height - 1, "▼", Theme.dim)
        pageSize = area.height
        maxScroll = max
    }

    private var pageSize = 10
    private var maxScroll = 0

    /** The children as a board: open columns share the width, those without children are strips. */
    private fun drawChildren(c: Painter, top: Int, width: Int, columns: List<BoardColumn>, found: MutableList<Focusable>): Int {
        val open = columns.count { !it.collapsed }
        val strips = columns.size - open
        val openWidth = if (open == 0) 0 else ((width - strips * STRIP_WIDTH - (columns.size - 1)) / open).coerceAtLeast(12)
        // As tall as the most cards; the strips show as much of their names as fits.
        val height = COLUMN_HEADER + (columns.filter { !it.collapsed }.maxOfOrNull { col -> col.cards.sumOf { cardHeight(it, openWidth) } } ?: 0)
        var x = 0
        for (col in columns) {
            if (col.collapsed) {
                c.collapsedColumn(x, top, height, col, selected = false, click = null)
                x += STRIP_WIDTH + 1
                continue
            }
            c.columnHeader(x, top, openWidth, col, selected = false, click = null)
            var y = top + COLUMN_HEADER
            for (card in col.cards) {
                val open = { nav.openTicket(card.id) }
                val h = c.card(x, y, openWidth, card, selected = focus == found.size, click = open)
                found += Focusable(y, h, open)
                y += h
            }
            x += openWidth + 1
        }
        return top + height
    }

    /** The body, rendered as markdown by Mordant, [width] wide; kept until the body or the width change. */
    private fun bodyLines(width: Int): List<List<Cell>> {
        val key = state.body to width
        body?.takeIf { it.first == key }?.let { return it.second }
        val lines = Markdown(state.body).render(nav.terminal, width).lines.map { line ->
            line.flatMap { span -> graphemes(span.text).flatMap { g -> if (cellWidth(g) == 2) listOf(Cell(g, span.style), Cell("", span.style)) else listOf(Cell(g, span.style)) } }
        }
        body = key to lines
        return lines
    }

    private fun drawConfirmation(p: Painter, action: TicketAction) {
        val (title, text, confirm) = confirmation(action)
        val w = minOf(p.width - 4, 72)
        val textLines = wrap(text, w - 4)
        val h = textLines.size + if (action == TicketAction.REOPEN) 9 else 6
        val x = (p.width - w) / 2
        val y = ((p.height - h) / 2).coerceAtLeast(0)
        p.region(0, 0, p.width, p.height, click = {}, scroll = {})
        p.fill(x, y, w, h)
        p.box(x, y, w, h, Theme.accent, Border.DOUBLE)
        p.text(x + 2, y + 1, title, Theme.bold, max = w - 4)
        textLines.forEachIndexed { i, line -> p.text(x + 2, y + 3 + i, line) }
        var by = y + 3 + textLines.size + 1
        if (action == TicketAction.REOPEN) {
            p.text(x + 2, by, "What did the review find?", Theme.dim)
            val shown = reason.toString().let { r -> if (textWidth(r) > w - 7) "…" + r.takeLast(w - 8) else r }
            val end = p.runs(x + 2, by + 1, listOf(Run("> ", Theme.accent), Run(shown)))
            p.text(end, by + 1, "▏", Theme.accent)
            by += 3
        }
        val valid = action != TicketAction.REOPEN || reason.isNotBlank()
        val bx = p.button(x + 2, by, "$confirm (⏎)", enabled = valid) { confirm() } + 2
        p.button(bx, by, "Cancel (esc)") { confirming = null }
    }

    /** The question for [action]: title, what it does, and its button. */
    private fun confirmation(action: TicketAction): Triple<String, String, String> {
        val where = Workplace.current().text
        return when (action) {
            TicketAction.START -> Triple(
                "Start $id?",
                "Creates the branch $id with the ticket committed as in-progress, and its worktree. Then opens a shell there, $where.",
                "Start",
            )
            TicketAction.START_IN_CLAUDE, TicketAction.START_IN_CODEX -> Triple(
                "Start $id in ${action.agent!!.label}?",
                "Creates the branch $id with the ticket committed as in-progress, and its worktree. " +
                    "Then runs ${action.agent!!.label} there, $where, waiting for your instructions.",
                "Start in ${action.agent!!.label}",
            )
            TicketAction.CHECK_OUT -> Triple(
                "Create the worktree for $id?",
                "$id was started on another computer. Adds its worktree here, with a local branch $id tracking the remote one, " +
                    "as last fetched. Then opens a shell there, $where. Nothing is committed.",
                "Create worktree",
            )
            TicketAction.FINISH -> Triple("Set $id to review?", "Sets the ticket to review, logs it, and commits only the ticket on its branch.", "Set to review")
            TicketAction.MERGE -> Triple(
                "Merge and finish $id?",
                "Merges the branch $id into its target with a merge commit that also sets the ticket to done, " +
                    "then removes its worktree and deletes the branch. Nothing is pushed.",
                "Merge and finish",
            )
            TicketAction.REOPEN -> Triple(
                "Send $id back to in progress?",
                "Sets the ticket to in-progress again and logs the reason, committing only the ticket on its branch.",
                "Back to in progress",
            )
            TicketAction.OPEN_TERMINAL, TicketAction.DIFF -> Triple("", "", "")
        }
    }

    /** The user asked for [action]: it runs now, or once confirmed when it changes the repository. */
    private fun request(action: TicketAction) {
        if (busy) return
        // Said before starting anything, rather than by a shell that fails once the ticket is started.
        val agent = action.agent
        if (agent != null && !nav.workplace.has(agent.option)) {
            notice = Notice("${agent.label} isn't installed: no `${agent.option}` command found. Start opens a shell.", error = true)
            return
        }
        if (action.confirmed) {
            confirming = action
            reason.clear()
        } else {
            run(action)
        }
    }

    private fun confirm() {
        val action = confirming ?: return
        if (action == TicketAction.REOPEN && reason.isBlank()) return
        confirming = null
        run(action)
    }

    private fun run(action: TicketAction) {
        val size = nav.store.snapshot.value.graph?.ticket(id)?.frontmatter?.size
        val store = nav.store
        when (action) {
            TicketAction.START -> act { start(agent = null, size) }
            TicketAction.START_IN_CLAUDE, TicketAction.START_IN_CODEX -> act { start(action.agent, size) }
            TicketAction.CHECK_OUT -> act {
                when (val outcome = store.checkOut(id)) {
                    is StartOutcome.NotStarted -> Notice("No worktree added: ${outcome.reason}", error = true)
                    is StartOutcome.Started -> outcome.workspace?.let { nav.work(it, id, agent = null, size, done = "Worktree added in $it") }
                }
            }
            TicketAction.OPEN_TERMINAL -> act {
                when (val workspace: Path? = store.workspace(id)) {
                    null -> Notice("Branch '$id' isn't checked out anywhere: add a worktree for it (git worktree add).", error = true)
                    else -> nav.work(workspace, id, agent = null, size)
                }
            }
            TicketAction.DIFF -> act { diff() }
            TicketAction.FINISH -> act { store.finish(id).notice }
            TicketAction.MERGE -> act { store.merge(id).notice }
            TicketAction.REOPEN -> reason.toString().trim().let { r -> act { store.reopen(id, r).notice } }
        }
    }

    private suspend fun start(agent: SessionAgent?, size: Size?): Notice? = when (val outcome = nav.store.start(id)) {
        is StartOutcome.NotStarted -> Notice("Not started: ${outcome.reason}", error = true)
        is StartOutcome.Started -> when (val workspace = outcome.workspace) {
            null -> Notice("Started, but ${outcome.note}", error = true)
            else -> nav.work(workspace, id, agent, size, done = "Started in $workspace")
        }
    }

    /**
     * The ticket's changes against its target in git's diff tool, on this terminal until it closes:
     * a terminal one (vimdiff) works too, and so does the one git guesses when none is set.
     */
    private suspend fun diff(): Notice? = when (val outcome = nav.store.diff(id)) {
        is DiffOutcome.NotShown -> Notice("No diff: ${outcome.reason}", error = true)
        is DiffOutcome.Ready -> {
            var exit: Int? = 0
            nav.outside {
                nav.terminal.println("Safanoria · $id: its changes against ${outcome.target} in the diff tool. Close it to come back.")
                outcome.warnings.forEach { nav.terminal.println("warning: $it") }
                exit = runHere(outcome.directory.toString(), outcome.command)
            }
            when (exit) {
                null -> Notice("Couldn't run git", error = true)
                0 -> outcome.warnings.takeIf { it.isNotEmpty() }?.let { w -> Notice(w.joinToString("\n") { "Warning: $it" }, error = false) }
                else -> Notice("git difftool ended with $exit", error = true)
            }
        }
    }

    /** Runs one action at a time; its result replaces the previous notice. */
    private fun act(action: suspend () -> Notice?) {
        if (busy) return
        busy = true
        notice = null
        nav.launch {
            notice = try { action() } catch (e: Exception) { Notice(e.message ?: e.toString(), error = true) }
            busy = false
        }
    }

    override fun key(e: KeyboardEvent): Boolean {
        confirming?.let { return confirmKey(e, it) }
        when (e.key) {
            "ArrowUp", "k" -> scroll -= 1
            "ArrowDown", "j" -> scroll += 1
            "PageUp" -> scroll -= pageSize - 1
            "PageDown", " " -> scroll += pageSize - 1
            "Home", "g" -> scroll = 0
            "End", "G" -> scroll = maxScroll
            "Tab" -> moveFocus(if (e.shift) -1 else 1)
            "Enter" -> focusables.getOrNull(focus)?.activate?.invoke()
            "ArrowLeft", "h" -> nav.back()
            "p" -> state.parent?.let { nav.openTicket(it.id) }
            else -> {
                val action = state.actions.firstOrNull { it.key == e.key } ?: return false
                request(action)
            }
        }
        return true
    }

    private fun moveFocus(by: Int) {
        if (focusables.isEmpty()) return
        focus = if (focus < 0) (if (by > 0) 0 else focusables.size - 1) else (focus + by).mod(focusables.size)
        followFocus = true
    }

    private fun confirmKey(e: KeyboardEvent, action: TicketAction): Boolean {
        when {
            e.key == "Enter" -> confirm()
            e.key == "Escape" -> confirming = null
            action == TicketAction.REOPEN -> when {
                e.key == "Backspace" -> if (reason.isNotEmpty()) reason.setLength(reason.length - 1)
                e.key.length == 1 || e.key.length == 2 && e.key[0].isHighSurrogate() -> if (!e.ctrl && !e.alt) reason.append(e.key)
            }
            e.key == "y" -> confirm()
            e.key == "n" || e.key == "q" || e.key == "Backspace" -> confirming = null
        }
        return true
    }
}

/** The key that runs the action on the ticket screen. */
internal val TicketAction.key: String
    get() = when (this) {
        TicketAction.START -> "s"
        TicketAction.START_IN_CLAUDE -> "c"
        TicketAction.START_IN_CODEX -> "x"
        TicketAction.CHECK_OUT -> "w"
        TicketAction.OPEN_TERMINAL -> "o"
        TicketAction.DIFF -> "d"
        TicketAction.FINISH -> "f"
        TicketAction.MERGE -> "m"
        TicketAction.REOPEN -> "i"
    }

internal val TicketAction.label: String
    get() = when (this) {
        TicketAction.START -> "Start"
        TicketAction.START_IN_CLAUDE -> "Start in Claude Code"
        TicketAction.START_IN_CODEX -> "Start in Codex"
        TicketAction.CHECK_OUT -> "Create worktree"
        TicketAction.OPEN_TERMINAL -> "Open shell"
        TicketAction.DIFF -> "Diff"
        TicketAction.FINISH -> "Finish"
        TicketAction.MERGE -> "Merge"
        TicketAction.REOPEN -> "Back to in progress"
    }
