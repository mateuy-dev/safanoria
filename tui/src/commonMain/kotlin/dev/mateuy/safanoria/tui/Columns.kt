package dev.mateuy.safanoria.tui

import com.github.ajalt.mordant.rendering.TextStyle
import dev.mateuy.safanoria.core.Priority
import dev.mateuy.safanoria.core.TicketType
import dev.mateuy.safanoria.core.text
import dev.mateuy.safanoria.views.BoardColumn
import dev.mateuy.safanoria.views.TicketCard

/** Width of a collapsed column: a strip with its count and its name downwards. */
internal const val STRIP_WIDTH = 3

/** The narrowest an open column gets; narrower, the board scrolls sideways. */
internal const val MIN_COLUMN_WIDTH = 24

/** Rows above a column's cards: its name, and a line in its color. */
internal const val COLUMN_HEADER = 2

/**
 * What a card shows inside its border, [width] cells wide: the same as the desktop app's cards.
 * Its id, title (up to 3 lines), progress, facts, problems and blockers.
 */
internal fun cardLines(card: TicketCard, width: Int): List<List<Run>> {
    val lines = mutableListOf<List<Run>>()
    lines += listOf(Run(fit(card.id, width), Theme.dim))
    val title = wrap(card.title, width)
    title.take(3).forEachIndexed { i, line -> lines += listOf(Run(if (i == 2 && title.size > 3) fit("$line …", width) else line, Theme.bold)) }
    card.progress?.let {
        val bar = minOf(10, width - 6).coerceAtLeast(3)
        val done = if (it.total == 0) 0 else (bar * it.done + it.total / 2) / it.total
        lines += listOf(Run("▰".repeat(done), Theme.success), Run("▱".repeat(bar - done), Theme.dim), Run(" ${it.done}/${it.total}"))
    }
    val facts = listOfNotNull(
        card.type?.takeIf { it != TicketType.FEATURE }?.text,
        card.priority?.takeIf { it != Priority.MEDIUM }?.text,
        card.size?.text,
        card.parentId?.let { "↑ $it" },
        card.onlyOnBranch?.let { "only on ${card.remoteBranch ?: it}" },
        card.remoteBranch?.takeIf { card.onlyOnBranch == null }?.let { "on $it" },
        card.versions,
    ) + card.tags.map { "#$it" }
    if (facts.isNotEmpty()) {
        val style = card.priority?.takeIf { it >= Priority.HIGH }?.let { TextStyle(Theme.priority(it)) } ?: Theme.dim
        wrap(facts.joinToString(" · "), width).take(2).forEach { lines += listOf(Run(it, style)) }
    }
    if (card.errors + card.warnings > 0) {
        val counts = listOfNotNull(
            card.errors.takeIf { it > 0 }?.let { "$it error${if (it == 1) "" else "s"}" },
            card.warnings.takeIf { it > 0 }?.let { "$it warning${if (it == 1) "" else "s"}" },
        )
        lines += listOf(Run(fit("⚠ " + counts.joinToString(", "), width), if (card.errors > 0) Theme.error else Theme.warning))
    }
    if (card.openBlockers.isNotEmpty()) {
        wrap("blocked by " + card.openBlockers.joinToString(", "), width).take(2).forEach { lines += listOf(Run(it, Theme.error)) }
    }
    return lines
}

/** Rows a card takes in a column [width] wide, border included. */
internal fun cardHeight(card: TicketCard, width: Int) = cardLines(card, width - 4).size + 2

/** A card in a box, [width] wide; [selected] has a heavy border in the accent color. Clicking it calls [click]. */
internal fun Painter.card(x: Int, y: Int, width: Int, card: TicketCard, selected: Boolean, click: () -> Unit): Int {
    val lines = cardLines(card, width - 4)
    val height = lines.size + 2
    box(x, y, width, height, if (selected) Theme.accent else Theme.dim, if (selected) Border.HEAVY else Border.ROUNDED)
    lines.forEachIndexed { i, runs -> runs(x + 2, y + 1 + i, runs, max = width - 4) }
    region(x, y, width, height, click = click)
    return height
}

/** A column's name and count over a line in its color. [selected] when the keyboard is on the column itself. */
internal fun Painter.columnHeader(x: Int, y: Int, width: Int, column: BoardColumn, selected: Boolean, click: (() -> Unit)?) {
    val color = TextStyle(Theme.group(column.group))
    val label = (if (selected) Theme.bold + Theme.selected else Theme.bold)
    val end = runs(x, y, listOf(Run("● ", color), Run(column.group.columnLabel, label)), max = width - 4)
    text(end + 1, y, "${column.cards.size}", Theme.dim)
    fill(x, y + 1, width, 1, color, "─")
    if (click != null) region(x, y, width, COLUMN_HEADER, click = click)
}

/** A collapsed column: a strip with a dot, the count and the name written downwards. */
internal fun Painter.collapsedColumn(x: Int, y: Int, height: Int, column: BoardColumn, selected: Boolean, click: (() -> Unit)?) {
    val color = TextStyle(Theme.group(column.group))
    text(x + 1, y, "●", color)
    val count = "${column.cards.size}"
    text(x + (STRIP_WIDTH - count.length).coerceAtLeast(0) / 2, y + 1, fit(count, STRIP_WIDTH), Theme.dim)
    graphemes(column.group.columnLabel).take(height - 3).forEachIndexed { i, g ->
        text(x + 1, y + 3 + i, g, if (selected) Theme.bold + Theme.selected else Theme.bold)
    }
    if (click != null) region(x, y, STRIP_WIDTH, height, click = click)
}

/**
 * Where columns go across [available] cells: collapsed ones are strips, open ones share the rest,
 * one cell apart, but no narrower than [MIN_COLUMN_WIDTH]. Returns each column's x and width; the
 * last one may end past [available], and then the board scrolls.
 */
internal fun columnLayout(columns: List<BoardColumn>, available: Int): List<Pair<Int, Int>> {
    val open = columns.count { !it.collapsed }
    val strips = columns.size - open
    val gaps = (columns.size - 1).coerceAtLeast(0)
    val openWidth = if (open == 0) 0 else maxOf(MIN_COLUMN_WIDTH, (available - strips * STRIP_WIDTH - gaps) / open)
    var x = 0
    return columns.map { column ->
        val w = if (column.collapsed) STRIP_WIDTH else openWidth
        (x to w).also { x += w + 1 }
    }
}
