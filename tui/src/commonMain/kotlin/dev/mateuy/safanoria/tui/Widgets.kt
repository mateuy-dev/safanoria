package dev.mateuy.safanoria.tui

import com.github.ajalt.colormath.model.RGB
import com.github.ajalt.mordant.rendering.TextStyle
import dev.mateuy.safanoria.core.Status

/** `[label]`, clickable unless disabled; [focused] when the keyboard is on it. Returns the column after it. */
internal fun Painter.button(x: Int, y: Int, label: String, focused: Boolean = false, enabled: Boolean = true, click: () -> Unit): Int {
    val text = "[$label]"
    val style = when {
        !enabled -> Theme.dim
        focused -> Theme.accent + Theme.selected
        else -> Theme.accent
    }
    val end = text(x, y, text, style)
    if (enabled) region(x, y, end - x, 1, click = click)
    return end
}

/** The width [button] takes. */
internal fun buttonWidth(label: String) = textWidth(label) + 2

/** A filter: `● label` when [selected], `○ label` when not. Returns the column after it. */
internal fun Painter.chip(x: Int, y: Int, label: String, selected: Boolean, focused: Boolean, click: () -> Unit): Int {
    var style = if (selected) Theme.accent else Plain
    if (focused) style += Theme.selected
    val end = text(x, y, (if (selected) "● " else "○ ") + label, style)
    region(x, y, end - x, 1, click = click)
    return end
}

/** The ticket's status as a colored label. Returns the column after it. */
internal fun Painter.statusBadge(x: Int, y: Int, status: Status?): Int =
    if (status == null) text(x, y, " unreadable status ", Theme.error + Theme.selected)
    else text(x, y, " ${status.label} ", TextStyle(RGB("#FFFFFF"), Theme.status(status), bold = true))

/** The last row: what the keys do, or [message] when there is one. */
internal fun Painter.statusBar(hints: String, message: Pair<String, TextStyle>? = null) {
    val y = height - 1
    fill(0, y, width, 1)
    if (message != null) text(1, y, message.first.replace('\n', ' '), message.second, max = width - 2)
    else text(1, y, hints, Theme.dim, max = width - 2)
}

/** A line across the width. */
internal fun Painter.rule(y: Int, style: TextStyle = Theme.dim) = fill(0, y, width, 1, style, "─")
