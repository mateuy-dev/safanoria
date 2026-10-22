package dev.mateuy.safanoria.tui

import dev.mateuy.safanoria.views.BoardColumn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CanvasTest {
    private fun Canvas.line(y: Int) = rows[y].joinToString("") { it.text }.trimEnd()

    @Test
    fun wrapBreaksBetweenWordsAndCutsLongOnes() {
        assertEquals(listOf("one two", "three"), wrap("one two three", 8))
        assertEquals(listOf("abcd", "efgh", "ij"), wrap("abcdefghij", 4))
        assertEquals(listOf("a", "", "b"), wrap("a\n\nb", 10))
    }

    @Test
    fun fitEndsWhatIsCutInAnEllipsis() {
        assertEquals("short", fit("short", 5))
        assertEquals("shor…", fit("shorter", 5))
        assertEquals("", fit("x", 0))
    }

    @Test
    fun wideCharactersTakeTwoCells() {
        assertEquals(4, textWidth("😀ab"))
        assertEquals(1, textWidth("e\u0301")) // e and a combining accent
        val canvas = Canvas(6, 1)
        canvas.painter().text(0, 0, "日本x")
        assertEquals("日本x", canvas.line(0))
    }

    @Test
    fun paintersClipAndScroll() {
        val canvas = Canvas(10, 3)
        val area = canvas.painter().area(2, 1, 4, 1)
        area.text(0, 0, "abcdefgh")
        area.scrolled(5).text(0, 0, "above") // row 0 of content scrolled 5 down: out of sight
        assertEquals("", canvas.line(0))
        assertEquals("  abcd", canvas.line(1))
        assertEquals("", canvas.line(2))
    }

    @Test
    fun regionsAreClippedAndTheLatestIsOnTop() {
        val canvas = Canvas(10, 5)
        var clicked = ""
        val p = canvas.painter()
        p.region(0, 0, 10, 5, click = { clicked = "back" })
        p.area(2, 2, 3, 1).region(0, 0, 8, 8, click = { clicked = "front" })
        canvas.hit(3, 2) { it.click }!!.invoke()
        assertEquals("front", clicked)
        canvas.hit(6, 2) { it.click }!!.invoke()
        assertEquals("back", clicked)
        assertNull(canvas.hit(3, 2) { it.scroll })
    }

    @Test
    fun openColumnsShareTheWidthAndCollapsedOnesAreStrips() {
        val columns = listOf(BoardColumn(null, emptyList()), BoardColumn(null, emptyList(), collapsed = true), BoardColumn(null, emptyList()))
        assertEquals(listOf(0 to 47, 48 to 3, 52 to 47), columnLayout(columns, 100))
        // Too narrow: the minimum width, and the board scrolls.
        assertEquals(listOf(0 to MIN_COLUMN_WIDTH, 25 to 3, 29 to MIN_COLUMN_WIDTH), columnLayout(columns, 40))
    }
}
