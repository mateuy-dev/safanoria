package dev.mateuy.safanoria.tui

import com.github.ajalt.mordant.rendering.TextStyle

/** What a cell shows: a character with its combining marks, or "" for the right half of a wide one. */
internal data class Cell(val text: String, val style: TextStyle)

internal val Plain = TextStyle()

private val Blank = Cell(" ", Plain)

/** Something on screen that reacts to the mouse: a click, or the wheel ([scroll] gets +1 down, -1 up). */
internal class Region(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val click: (() -> Unit)? = null,
    val scroll: ((Int) -> Unit)? = null,
) {
    fun contains(px: Int, py: Int) = px >= x && px < x + width && py >= y && py < y + height
}

/** One frame: the cells of the screen, and the [regions] drawn on it, latest on top. */
internal class Canvas(val width: Int, val height: Int) {
    val rows: Array<Array<Cell>> = Array(height) { Array(width) { Blank } }
    val regions = mutableListOf<Region>()

    /** The whole screen to draw on. */
    fun painter() = Painter(this, 0, 0, Rect(0, 0, width, height))

    /** The handler on top at ([x], [y]) that [select] picks. */
    fun <T : Any> hit(x: Int, y: Int, select: (Region) -> T?): T? = regions.lastOrNull { it.contains(x, y) && select(it) != null }?.let(select)
}

internal data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
    val right get() = x + width
    val bottom get() = y + height

    fun intersect(o: Rect): Rect {
        val l = maxOf(x, o.x)
        val t = maxOf(y, o.y)
        return Rect(l, t, maxOf(0, minOf(right, o.right) - l), maxOf(0, minOf(bottom, o.bottom) - t))
    }
}

/**
 * Draws on a [Canvas] in its own coordinates: (0, 0) is its [originX], [originY] on screen, and
 * nothing is drawn outside [clip] (screen coordinates). [area] gives a painter for a part of this
 * one, [scrolled] one whose content starts above it.
 */
internal class Painter(private val canvas: Canvas, private val originX: Int, private val originY: Int, private val clip: Rect) {
    val width: Int get() = clip.right - originX
    val height: Int get() = clip.bottom - originY

    /** The part at ([x], [y]) of [width] × [height], clipped to this one. */
    fun area(x: Int, y: Int, width: Int, height: Int) =
        Painter(canvas, originX + x, originY + y, clip.intersect(Rect(originX + x, originY + y, width, height)))

    /** The same area, with its content [dy] rows up (scrolled down by [dy]) and [dx] columns left. */
    fun scrolled(dy: Int, dx: Int = 0) = Painter(canvas, originX - dx, originY - dy, clip)

    fun set(x: Int, y: Int, cell: Cell) {
        val sx = originX + x
        val sy = originY + y
        if (sx < clip.x || sx >= clip.right || sy < clip.y || sy >= clip.bottom) return
        canvas.rows[sy][sx] = cell
    }

    /**
     * Writes [text] from ([x], [y]) on one line, at most [max] cells: what doesn't fit ends in "…".
     * Returns the column after it.
     */
    fun text(x: Int, y: Int, text: String, style: TextStyle = Plain, max: Int = Int.MAX_VALUE): Int {
        val fitted = fit(text, max)
        var cx = x
        for (g in graphemes(fitted)) {
            val w = cellWidth(g)
            if (w == 0) continue
            set(cx, y, Cell(g, style))
            if (w == 2) set(cx + 1, y, Cell("", style))
            cx += w
        }
        return cx
    }

    /** Pieces of text in their styles, one after the other; returns the column after them. */
    fun runs(x: Int, y: Int, runs: List<Run>, max: Int = Int.MAX_VALUE): Int {
        var cx = x
        for (run in runs) {
            val left = max - (cx - x)
            if (left <= 0) break
            cx = text(cx, y, run.text, run.style, left)
        }
        return cx
    }

    fun fill(x: Int, y: Int, width: Int, height: Int, style: TextStyle = Plain, char: String = " ") {
        for (row in y until y + height) for (col in x until x + width) set(col, row, Cell(char, style))
    }

    /** A box border of [width] × [height] in [border] lines. */
    fun box(x: Int, y: Int, width: Int, height: Int, style: TextStyle, border: Border = Border.ROUNDED) {
        if (width < 2 || height < 2) return
        val c = border.chars
        set(x, y, Cell(c[0], style)); set(x + width - 1, y, Cell(c[2], style))
        set(x, y + height - 1, Cell(c[5], style)); set(x + width - 1, y + height - 1, Cell(c[7], style))
        for (col in x + 1 until x + width - 1) { set(col, y, Cell(c[1], style)); set(col, y + height - 1, Cell(c[6], style)) }
        for (row in y + 1 until y + height - 1) { set(x, row, Cell(c[3], style)); set(x + width - 1, row, Cell(c[4], style)) }
    }

    /** Makes ([x], [y], [width] × [height]) react to the mouse, where visible. */
    fun region(x: Int, y: Int, width: Int, height: Int, click: (() -> Unit)? = null, scroll: ((Int) -> Unit)? = null) {
        val r = Rect(originX + x, originY + y, width, height).intersect(clip)
        if (r.width > 0 && r.height > 0) canvas.regions += Region(r.x, r.y, r.width, r.height, click, scroll)
    }
}

internal data class Run(val text: String, val style: TextStyle = Plain)

/** Corners and sides: top-left, top, top-right, left, right, bottom-left, bottom, bottom-right. */
internal enum class Border(val chars: List<String>) {
    ROUNDED(listOf("╭", "─", "╮", "│", "│", "╰", "─", "╯")),
    HEAVY(listOf("┏", "━", "┓", "┃", "┃", "┗", "━", "┛")),
    DOUBLE(listOf("╔", "═", "╗", "║", "║", "╚", "═", "╝")),
}

/** [text] split in what each cell shows: a character with the zero-width ones that follow it. Control characters become spaces. */
internal fun graphemes(text: String): List<String> {
    val out = mutableListOf<String>()
    var i = 0
    while (i < text.length) {
        val cp = codePointAt(text, i)
        val len = if (cp > 0xFFFF) 2 else 1
        val s = if (cp < 0x20 || cp == 0x7F) " " else text.substring(i, i + len)
        if (cellWidth(cp) == 0 && out.isNotEmpty()) out[out.size - 1] = out.last() + s else out += s
        i += len
    }
    return out
}

private fun codePointAt(text: String, i: Int): Int {
    val c = text[i]
    if (c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) {
        return 0x10000 + ((c.code - 0xD800) shl 10) + (text[i + 1].code - 0xDC00)
    }
    return c.code
}

internal fun cellWidth(grapheme: String): Int = if (grapheme.isEmpty()) 0 else cellWidth(codePointAt(grapheme, 0))

/**
 * Cells a code point takes: 0 for combining marks and other zero-width ones, 2 for East Asian wide
 * characters and emoji, 1 otherwise. An approximation, as terminals themselves differ.
 */
internal fun cellWidth(cp: Int): Int = when {
    cp in 0x0300..0x036F || cp in 0x200B..0x200F || cp in 0xFE00..0xFE0F || cp == 0x20E3 || cp in 0x1F3FB..0x1F3FF -> 0
    cp in 0x1100..0x115F || cp in 0x2E80..0xA4CF || cp in 0xAC00..0xD7A3 || cp in 0xF900..0xFAFF ||
        cp in 0xFE30..0xFE4F || cp in 0xFF00..0xFF60 || cp in 0xFFE0..0xFFE6 ||
        cp in 0x1F300..0x1F64F || cp in 0x1F900..0x1F9FF || cp in 0x20000..0x3FFFD -> 2
    else -> 1
}

/** Cells [text] takes on one line. */
internal fun textWidth(text: String): Int = graphemes(text).sumOf { cellWidth(it) }

/** [text] cut to [max] cells, ending in "…" when cut. */
internal fun fit(text: String, max: Int): String {
    if (max <= 0) return ""
    if (textWidth(text) <= max) return text
    val out = StringBuilder()
    var w = 0
    for (g in graphemes(text)) {
        val cw = cellWidth(g)
        if (w + cw > max - 1) break
        out.append(g)
        w += cw
    }
    return out.append("…").toString()
}

/** [text] in lines of at most [width] cells, broken between words; a word longer than a line is cut. Keeps its line breaks. */
internal fun wrap(text: String, width: Int): List<String> {
    if (width <= 0) return emptyList()
    val lines = mutableListOf<String>()
    for (paragraph in text.split('\n')) {
        var line = StringBuilder()
        var lineWidth = 0
        for (word in paragraph.split(' ').filter { it.isNotEmpty() }) {
            var w = word
            while (textWidth(w) > width) {
                if (lineWidth > 0) { lines += line.toString(); line = StringBuilder(); lineWidth = 0 }
                val head = StringBuilder()
                var hw = 0
                val gs = graphemes(w)
                var n = 0
                while (n < gs.size && hw + cellWidth(gs[n]) <= width) { head.append(gs[n]); hw += cellWidth(gs[n]); n++ }
                lines += head.toString()
                w = gs.drop(n).joinToString("")
            }
            if (w.isEmpty()) continue
            val ww = textWidth(w)
            if (lineWidth > 0 &&lineWidth + 1 + ww > width) { lines += line.toString(); line = StringBuilder(); lineWidth = 0 }
            if (lineWidth > 0) { line.append(' '); lineWidth++ }
            line.append(w)
            lineWidth += ww
        }
        lines += line.toString()
    }
    return lines
}
