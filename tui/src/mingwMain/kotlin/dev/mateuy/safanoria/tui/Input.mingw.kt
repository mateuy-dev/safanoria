package dev.mateuy.safanoria.tui

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.MouseTracking
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.terminal.Terminal
import kotlin.time.Duration.Companion.milliseconds

// The Windows console gives events, not bytes: Mordant reads them right, the wheel too.
internal actual fun openInput(terminal: Terminal): Input {
    val raw = terminal.enterRawMode(MouseTracking.Normal)
    return object : Input {
        override fun read(): InputEvent? = raw.readEventOrNull(100.milliseconds)

        override fun close() = raw.close()
    }
}
