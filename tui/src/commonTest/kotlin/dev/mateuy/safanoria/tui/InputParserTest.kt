package dev.mateuy.safanoria.tui

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InputParserTest {
    /** The events in [input], as a terminal sends it in raw mode. */
    private fun events(input: String): List<InputEvent?> {
        val bytes = input.encodeToByteArray()
        var i = 0
        val parser = InputParser { if (i < bytes.size) bytes[i++].toInt() and 0xff else null }
        return buildList { while (i < bytes.size) add(parser.read()) }
    }

    private fun event(input: String) = events(input).single()

    @Test
    fun charactersAndControlKeys() {
        assertEquals(listOf<InputEvent?>(KeyboardEvent("a"), KeyboardEvent("ñ"), KeyboardEvent("😀")), events("añ😀"))
        assertEquals(KeyboardEvent("Enter"), event("\r"))
        assertEquals(KeyboardEvent("Tab"), event("\t"))
        assertEquals(KeyboardEvent("Backspace"), event("\u007f"))
        assertEquals(KeyboardEvent("c", ctrl = true), event("\u0003"))
    }

    @Test
    fun escapeAloneIsTheEscapeKey() {
        assertEquals(KeyboardEvent("Escape"), event("\u001b"))
        assertEquals(KeyboardEvent("x", alt = true), event("\u001bx"))
    }

    @Test
    fun specialKeys() {
        assertEquals(KeyboardEvent("ArrowUp"), event("\u001b[A"))
        assertEquals(KeyboardEvent("ArrowLeft"), event("\u001bOD"))
        assertEquals(KeyboardEvent("ArrowRight", ctrl = true), event("\u001b[1;5C"))
        assertEquals(KeyboardEvent("Tab", shift = true), event("\u001b[Z"))
        assertEquals(KeyboardEvent("PageDown"), event("\u001b[6~"))
        assertEquals(KeyboardEvent("Home"), event("\u001b[H"))
        assertEquals(KeyboardEvent("End"), event("\u001b[4~"))
        assertNull(event("\u001b[I")) // focus in: not a key
    }

    @Test
    fun sgrMouse() {
        assertEquals(MouseEvent(x = 9, y = 4, left = true), event("\u001b[<0;10;5M"))
        assertEquals(MouseEvent(x = 9, y = 4), event("\u001b[<0;10;5m"))
        assertEquals(MouseEvent(x = 199, y = 0, wheelUp = true), event("\u001b[<64;200;1M"))
        assertEquals(MouseEvent(x = 0, y = 0, wheelDown = true, ctrl = true), event("\u001b[<81;1;1M"))
        assertEquals(MouseEvent(x = 2, y = 2, right = true), event("\u001b[<2;3;3M"))
    }

    @Test
    fun x10MouseTakesTheOffsetOff() {
        // Button byte 32 + 0, coordinates 32 + 1-based: a left click at (0, 0).
        assertEquals(MouseEvent(x = 0, y = 0, left = true), event("\u001b[M !!"))
        // 32 + 64: the wheel up, which Mordant 3.0.2 reads as a left click.
        assertEquals(MouseEvent(x = 4, y = 1, wheelUp = true), event("\u001b[M`%\""))
        assertEquals(MouseEvent(x = 0, y = 0), event("\u001b[M#!!"))
    }
}
