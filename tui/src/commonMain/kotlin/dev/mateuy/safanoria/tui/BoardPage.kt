package dev.mateuy.safanoria.tui

import com.github.ajalt.mordant.input.KeyboardEvent
import dev.mateuy.safanoria.core.BoardGroup
import dev.mateuy.safanoria.core.TicketFilter
import dev.mateuy.safanoria.core.TicketType
import dev.mateuy.safanoria.core.text
import dev.mateuy.safanoria.views.BoardColumn
import dev.mateuy.safanoria.views.BoardViewState
import dev.mateuy.safanoria.views.boardViewState

/**
 * The board: a column per status, cards in them, filters above, as in the desktop app. The
 * keyboard moves a selection across cards; Tab moves it to the filters.
 */
internal class BoardPage(private val nav: Navigator) : Page {
    /** Closed work is collapsed until asked for, as in the desktop app. */
    private val collapsed = mutableSetOf<BoardGroup?>(BoardGroup.RELEASED, BoardGroup.WONTFIX)
    private var filter = TicketFilter()
    private var showChildren = false
    private var state = BoardViewState()

    /** The selection: a column, and a card in it by id (by index when that card is gone). */
    private var column: BoardGroup? = null
    private var columnChosen = false
    private var cardId: String? = null
    private var cardIndex = 0

    /** Scrolling: rows down in each column, cells right across the board. */
    private val scrolls = mutableMapOf<BoardGroup?, Int>()
    private var boardScroll = 0

    /** True after a key moved the selection: the next frame scrolls to it. Mouse scrolling doesn't. */
    private var follow = true

    /** The filter the keyboard is on, when it is on the filter bar. */
    private var chipFocus: Int? = null

    private class Chip(val label: String, val selected: Boolean, val toggle: () -> Unit, val gapBefore: Boolean = false)

    private fun chips(): List<Chip> = buildList {
        TicketType.entries.forEach { type -> add(Chip(type.text, type in filter.types, { filter = filter.copy(types = filter.types.toggled(type)) })) }
        state.areas.forEachIndexed { i, area -> add(Chip(area, area in filter.areas, { filter = filter.copy(areas = filter.areas.toggled(area)) }, gapBefore = i == 0)) }
        state.tags.forEachIndexed { i, tag -> add(Chip("#$tag", tag in filter.tags, { filter = filter.copy(tags = filter.tags.toggled(tag)) }, gapBefore = i == 0)) }
        add(Chip("blocked", filter.blocked, { filter = filter.copy(blocked = !filter.blocked) }, gapBefore = true))
        if (filter != TicketFilter()) add(Chip("clear", false, { filter = TicketFilter() }))
        // Not a filter: clearing leaves it.
        add(Chip("children", showChildren, { showChildren = !showChildren }, gapBefore = true))
    }

    private fun <T> Set<T>.toggled(value: T) = if (value in this) this - value else this + value

    override fun draw(p: Painter) {
        state = boardViewState(nav.store.snapshot.value, collapsed, filter, showChildren)
        resolveSelection()
        val count = (if (state.shownCount < state.ticketCount) "${state.shownCount} of " else "") + "${state.ticketCount} tickets"
        val end = p.runs(1, 0, listOf(Run("Safanoria", Theme.accent), Run(" · Board · $count")))
        if (state.loading) p.text(end + 2, 0, "reading…", Theme.dim)
        val buttons = listOf("Versions" to { nav.openVersions() }, "Fetch" to { fetch() }, "Refresh" to { refresh() })
        var bx = p.width - 1 - buttons.sumOf { buttonWidth(it.first) + 1 }
        for ((label, action) in buttons) bx = p.button(bx, 0, label, enabled = !state.loading || label == "Versions", click = action) + 1

        var y = drawChips(p, 1)
        state.error?.let { error -> wrap(error, p.width - 2).take(3).forEach { p.text(1, y++, it, Theme.error) } }
        state.projectProblems.take(3).forEach { p.text(1, y++, it, Theme.error, max = p.width - 2) }
        if (state.projectProblems.size > 3) p.text(1, y++, "… ${state.projectProblems.size - 3} more: safanoria-cli validate", Theme.error)
        drawColumns(p.area(1, y + 1, p.width - 2, p.height - y - 2))
        follow = false

        p.statusBar(
            when {
                chipFocus != null -> "← → choose · space toggle · tab/esc back to the cards · x clear · ? keys"
                else -> "←→↑↓ move · ⏎ open · space collapse · tab filters · v versions · r refresh · F fetch · ? keys · q quit"
            },
        )
    }

    /** The filter bar from row [top], wrapped to the width. Returns the row after it. */
    private fun drawChips(p: Painter, top: Int): Int {
        var x = 1
        var y = top
        chips().forEachIndexed { i, chip ->
            val w = textWidth(chip.label) + 2 + if (chip.gapBefore) 4 else 2
            if (x > 1 && x + w > p.width) { x = 1; y++ }
            if (chip.gapBefore && x > 1) { p.text(x + 1, y, "│", Theme.dim); x += 4 } else if (x > 1) x += 2
            x = p.chip(x, y, chip.label, chip.selected, focused = chipFocus == i) { chip.toggle() }
        }
        return y + 1
    }

    private fun drawColumns(p: Painter) {
        val columns = state.columns
        if (columns.isEmpty()) return
        val layout = columnLayout(columns, p.width)
        val total = layout.last().let { it.first + it.second }
        val selected = columns.indexOfFirst { it.group == column }
        if (follow && selected >= 0) {
            val (sx, sw) = layout[selected]
            if (sx < boardScroll) boardScroll = sx
            if (sx + sw > boardScroll + p.width) boardScroll = sx + sw - p.width
        }
        boardScroll = boardScroll.coerceIn(0, (total - p.width).coerceAtLeast(0))
        val board = p.scrolled(0, boardScroll)
        columns.forEachIndexed { i, col ->
            val (x, w) = layout[i]
            val toggle = { toggleColumn(col.group) }
            if (col.collapsed) {
                board.collapsedColumn(x, 0, p.height, col, selected = i == selected, click = { select(col.group); toggle() })
            } else {
                board.columnHeader(x, 0, w, col, selected = i == selected && col.cards.isEmpty(), click = { select(col.group); toggle() })
                val (up, down) = drawCards(board.area(x, COLUMN_HEADER, w, p.height - COLUMN_HEADER), col, isSelected = i == selected)
                // More cards above or below: arrows at the end of the header.
                if (up || down) board.text(x + w - 3, 0, (if (up) "▲" else " ") + (if (down) "▼" else " "), Theme.dim)
            }
        }
        if (boardScroll > 0) p.text(0, 0, "‹", Theme.accent)
        if (boardScroll + p.width < total) p.text(p.width - 1, 0, "›", Theme.accent)
    }

    /** The cards of [col], scrolled; returns whether there are more above and below. */
    private fun drawCards(p: Painter, col: BoardColumn, isSelected: Boolean): Pair<Boolean, Boolean> {
        val heights = col.cards.map { cardHeight(it, p.width) }
        val total = heights.sum()
        var scroll = scrolls[col.group] ?: 0
        if (follow && isSelected && cardIndex in heights.indices) {
            val top = heights.take(cardIndex).sum()
            if (top < scroll) scroll = top
            if (top + heights[cardIndex] > scroll + p.height) scroll = top + heights[cardIndex] - p.height
        }
        scroll = scroll.coerceIn(0, (total - p.height).coerceAtLeast(0))
        scrolls[col.group] = scroll
        p.region(0, 0, p.width, p.height, scroll = { dy -> scrolls[col.group] = (scrolls[col.group] ?: 0) + dy * 3 })
        val content = p.scrolled(scroll)
        var y = 0
        col.cards.forEachIndexed { i, card ->
            if (y + heights[i] > scroll && y < scroll + p.height) {
                content.card(0, y, p.width, card, selected = isSelected && i == cardIndex && chipFocus == null) {
                    select(col.group, i)
                    nav.openTicket(card.id)
                }
            }
            y += heights[i]
        }
        if (col.cards.isEmpty()) p.text(1, 0, "no tickets", Theme.dim)
        return (scroll > 0) to (scroll + p.height < total)
    }

    /** Keeps the selection on a column that exists, and on a card in it; first on the first column with cards. */
    private fun resolveSelection() {
        val columns = state.columns
        if (columns.isEmpty()) return
        if (!columnChosen) {
            column = columns.firstOrNull { !it.collapsed && it.cards.isNotEmpty() }?.group ?: columns.first().group
        }
        val col = columns.firstOrNull { it.group == column } ?: columns.first().also { column = it.group }
        val index = col.cards.indexOfFirst { it.id == cardId }
        cardIndex = if (index >= 0) index else cardIndex.coerceIn(0, (col.cards.size - 1).coerceAtLeast(0))
        cardId = col.cards.getOrNull(cardIndex)?.id
    }

    private fun select(group: BoardGroup?, index: Int = cardIndex) {
        column = group
        columnChosen = true
        cardIndex = index
        cardId = state.columns.firstOrNull { it.group == group }?.cards?.getOrNull(index)?.id
        follow = true
    }

    private fun toggleColumn(group: BoardGroup?) {
        if (group in collapsed) collapsed -= group else collapsed += group
        follow = true
    }

    private fun refresh() = nav.launch { nav.store.refresh() }

    private fun fetch() = nav.launch { nav.store.fetch() }

    override fun key(e: KeyboardEvent): Boolean {
        chipFocus?.let { return chipKey(e, it) }
        val columns = state.columns
        val at = columns.indexOfFirst { it.group == column }.coerceAtLeast(0)
        val cards = columns.getOrNull(at)?.cards.orEmpty()
        when (e.key) {
            "ArrowLeft", "h" -> if (at > 0) select(columns[at - 1].group)
            "ArrowRight", "l" -> if (at < columns.size - 1) select(columns[at + 1].group)
            "ArrowUp", "k" -> if (cardIndex > 0) select(column, cardIndex - 1)
            "ArrowDown", "j" -> if (cardIndex < cards.size - 1) select(column, cardIndex + 1)
            "PageUp" -> select(column, (cardIndex - 5).coerceAtLeast(0))
            "PageDown" -> select(column, (cardIndex + 5).coerceAtMost((cards.size - 1).coerceAtLeast(0)))
            "Home", "g" -> select(column, 0)
            "End", "G" -> select(column, (cards.size - 1).coerceAtLeast(0))
            "Enter" -> when {
                columns.getOrNull(at)?.collapsed == true -> toggleColumn(column)
                else -> cardId?.let { nav.openTicket(it) }
            }
            " " -> if (columns.isNotEmpty()) toggleColumn(column)
            "Tab" -> chipFocus = 0
            "1", "2", "3", "4" -> TicketType.entries.getOrNull(e.key.toInt() - 1)?.let { filter = filter.copy(types = filter.types.toggled(it)) }
            "b" -> filter = filter.copy(blocked = !filter.blocked)
            "c" -> showChildren = !showChildren
            "x" -> filter = TicketFilter()
            "v" -> nav.openVersions()
            "F" -> if (!state.loading) fetch()
            else -> return false
        }
        return true
    }

    private fun chipKey(e: KeyboardEvent, focus: Int): Boolean {
        val chips = chips()
        when (e.key) {
            "ArrowLeft", "h" -> chipFocus = (focus - 1).coerceAtLeast(0)
            "ArrowRight", "l" -> chipFocus = (focus + 1).coerceAtMost(chips.size - 1)
            " ", "Enter" -> {
                chips.getOrNull(focus)?.toggle?.invoke()
                chipFocus = focus.coerceAtMost(chips().size - 1)
            }
            "x" -> filter = TicketFilter()
            "Tab", "Escape", "ArrowDown", "j" -> chipFocus = null
            else -> return false
        }
        return true
    }
}
