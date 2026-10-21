package dev.mateuy.safanoria.tui

import com.github.ajalt.mordant.rendering.Line
import com.github.ajalt.mordant.rendering.Lines
import com.github.ajalt.mordant.rendering.Span
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.rendering.WidthRange
import com.github.ajalt.mordant.terminal.Terminal

/**
 * Puts frames on the terminal's alternate screen, writing only the rows that changed since the
 * last one. Mordant turns the styles into the escape codes the terminal supports.
 */
internal class Display(private val terminal: Terminal) {
    private var shown: Canvas? = null

    /** Switches to the alternate screen, without a cursor. */
    fun enter() {
        terminal.rawPrint("${CSI}?1049h${CSI}?25l${CSI}2J")
        shown = null
    }

    /** Back to the screen as it was, with the cursor. */
    fun leave() {
        terminal.rawPrint("${CSI}0m${CSI}?25h${CSI}?1049l")
        shown = null
    }

    fun show(frame: Canvas) {
        val previous = shown?.takeIf { it.width == frame.width && it.height == frame.height }
        val out = StringBuilder()
        // Synchronized output: terminals that know it show the frame at once, without tearing.
        out.append("${CSI}?2026h")
        if (previous == null) out.append("${CSI}0m${CSI}2J")
        for (y in 0 until frame.height) {
            val row = frame.rows[y]
            if (previous != null && previous.rows[y].contentEquals(row)) continue
            out.append("${CSI}${y + 1};1H").append(terminal.render(RowWidget(row)))
        }
        out.append("${CSI}?2026l")
        terminal.rawPrint(out)
        shown = frame
    }

    /** One row of cells as a widget, for Mordant to render in the terminal's colors. */
    private class RowWidget(private val cells: Array<Cell>) : Widget {
        override fun measure(t: Terminal, width: Int) = WidthRange(cells.size, cells.size)

        override fun render(t: Terminal, width: Int): Lines {
            val spans = mutableListOf<Span>()
            var i = 0
            while (i < cells.size) {
                val style = cells[i].style
                val blank = cells[i].text.isBlank()
                val text = StringBuilder()
                while (i < cells.size && cells[i].style == style && (cells[i].text.isBlank() == blank || cells[i].text.isEmpty())) {
                    text.append(if (blank) " " else cells[i].text)
                    i++
                }
                spans += if (blank) Span.space(text.length, style) else Span.word(text.toString(), style)
            }
            return Lines(listOf(Line(spans, Plain)))
        }
    }
}
