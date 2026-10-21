package dev.mateuy.safanoria.tui

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import com.github.ajalt.mordant.terminal.Terminal

/** The keyboard and the mouse, in raw mode until closed. */
internal interface Input : AutoCloseable {
    /** The next event, or null when there was none for a moment (about 100 ms). */
    fun read(): InputEvent?
}

/** Raw mode on [terminal], with mouse clicks and the wheel. */
internal expect fun openInput(terminal: Terminal): Input

internal const val CSI = "\u001b["

/** Mouse reporting: clicks and the wheel (1000), as SGR sequences (1006), which have no limit on columns. */
internal const val MOUSE_ON = "${CSI}?1000h${CSI}?1006h"
internal const val MOUSE_OFF = "${CSI}?1006l${CSI}?1000l"

/**
 * Turns the bytes a POSIX terminal sends in raw mode into events: UTF-8 characters, control keys,
 * escape sequences for the special keys, and mouse reports (SGR, and the older X10 form).
 * [next] gives the next byte, or null when none came for a moment: an Esc alone is the Esc key.
 *
 * Mordant 3.0.2 has its own parser, but it turns the wheel into clicks (it doesn't take the 32
 * off the X10 button byte) and doesn't read SGR reports.
 */
internal class InputParser(private val next: () -> Int?) {
    fun read(): InputEvent? {
        val b = next() ?: return null
        return when (b) {
            0x1b -> escape()
            0x0d, 0x0a -> KeyboardEvent("Enter")
            0x09 -> KeyboardEvent("Tab")
            0x7f, 0x08 -> KeyboardEvent("Backspace")
            0x00 -> KeyboardEvent(" ", ctrl = true)
            in 0x01..0x1a -> KeyboardEvent(('a' + (b - 1)).toString(), ctrl = true)
            in 0x1c..0x1f -> null
            else -> utf8(b)?.let { KeyboardEvent(it) }
        }
    }

    private fun escape(): InputEvent? {
        val b = next() ?: return KeyboardEvent("Escape")
        return when (b) {
            '['.code -> csi()
            'O'.code -> next()?.let { special(it.toChar(), "") } ?: KeyboardEvent("O", alt = true)
            0x1b -> KeyboardEvent("Escape")
            else -> (if (b in 0x20..0x7e) b.toChar().toString() else utf8(b))?.let { KeyboardEvent(it, alt = true) }
        }
    }

    /** After `ESC [`: parameters, intermediate bytes, then the final byte (ECMA-48). */
    private fun csi(): InputEvent? {
        var c = next() ?: return KeyboardEvent("[", alt = true)
        if (c == 'M'.code) return x10Mouse()
        val params = StringBuilder()
        while (c in 0x30..0x3f) {
            params.append(c.toChar())
            c = next() ?: return null
        }
        while (c in 0x20..0x2f) c = next() ?: return null
        val final = c.toChar()
        if (params.startsWith("<")) return sgrMouse(params.substring(1), final)
        return special(final, params.toString())
    }

    /** A special key: arrows, Home/End, Page Up/Down…, with modifiers when the terminal says (`1;5A`). */
    private fun special(final: Char, params: String): InputEvent? {
        val parts = params.split(';')
        val mod = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
        val name = when (final) {
            'A' -> "ArrowUp"
            'B' -> "ArrowDown"
            'C' -> "ArrowRight"
            'D' -> "ArrowLeft"
            'H' -> "Home"
            'F' -> "End"
            'Z' -> return KeyboardEvent("Tab", shift = true)
            '~' -> when (parts[0]) {
                "1", "7" -> "Home"
                "4", "8" -> "End"
                "2" -> "Insert"
                "3" -> "Delete"
                "5" -> "PageUp"
                "6" -> "PageDown"
                else -> return null
            }
            else -> return null
        }
        return KeyboardEvent(name, ctrl = mod and 4 != 0, alt = mod and 2 != 0, shift = mod and 1 != 0)
    }

    /** `ESC [ < button ; x ; y M` (pressed) or `m` (released); x and y from 1. */
    private fun sgrMouse(params: String, final: Char): InputEvent? {
        val (b, x, y) = params.split(';').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 3 } ?: return null
        return mouse(b, x - 1, y - 1, pressed = final == 'M')
    }

    /** `ESC [ M` and three bytes, each 32 more than the value; x and y from 1. */
    private fun x10Mouse(): InputEvent? {
        val b = (next() ?: return null) - 32
        val x = (next() ?: return null) - 33
        val y = (next() ?: return null) - 33
        // Released: button 3, which doesn't say which one.
        return mouse(b, x, y, pressed = b and 3 != 3 || b and 64 != 0)
    }

    private fun mouse(b: Int, x: Int, y: Int, pressed: Boolean): InputEvent? {
        if (b and 32 != 0) return null // motion: not asked for
        val wheel = b and 64 != 0
        val button = b and 3
        return MouseEvent(
            x = x,
            y = y,
            left = pressed && !wheel && button == 0,
            middle = pressed && !wheel && button == 1,
            right = pressed && !wheel && button == 2,
            wheelUp = wheel && button == 0,
            wheelDown = wheel && button == 1,
            wheelLeft = wheel && button == 2,
            wheelRight = wheel && button == 3,
            shift = b and 4 != 0,
            alt = b and 8 != 0,
            ctrl = b and 16 != 0,
        )
    }

    /** The character that starts with byte [first], reading the rest of its UTF-8 bytes. */
    private fun utf8(first: Int): String? {
        val (count, initial) = when {
            first < 0x80 -> return first.toChar().toString()
            first and 0xe0 == 0xc0 -> 1 to (first and 0x1f)
            first and 0xf0 == 0xe0 -> 2 to (first and 0x0f)
            first and 0xf8 == 0xf0 -> 3 to (first and 0x07)
            else -> return null
        }
        var cp = initial
        repeat(count) {
            val b = next() ?: return null
            if (b and 0xc0 != 0x80) return null
            cp = (cp shl 6) or (b and 0x3f)
        }
        return if (cp > 0xffff) {
            val v = cp - 0x10000
            charArrayOf((0xd800 + (v shr 10)).toChar(), (0xdc00 + (v and 0x3ff)).toChar()).concatToString()
        } else {
            cp.toChar().toString()
        }
    }
}
