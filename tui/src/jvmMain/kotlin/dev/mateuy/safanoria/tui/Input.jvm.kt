package dev.mateuy.safanoria.tui

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.MouseTracking
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.terminal.Terminal
import java.io.FileDescriptor
import java.io.FileInputStream
import kotlin.time.Duration.Companion.milliseconds

// As on the native targets: on Windows Mordant's events, elsewhere Mordant's raw mode (reads that
// give up after 100 ms, which Java sees as -1) with the bytes parsed here.
internal actual fun openInput(terminal: Terminal): Input {
    if (System.getProperty("os.name").startsWith("Windows")) {
        val raw = terminal.enterRawMode(MouseTracking.Normal)
        return object : Input {
            override fun read(): InputEvent? = raw.readEventOrNull(100.milliseconds)

            override fun close() = raw.close()
        }
    }
    val raw = terminal.enterRawMode(MouseTracking.Off)
    terminal.rawPrint(MOUSE_ON)
    val stdin = FileInputStream(FileDescriptor.`in`)
    val parser = InputParser { stdin.read().takeIf { it >= 0 } }
    return object : Input {
        override fun read(): InputEvent? = parser.read()

        override fun close() {
            terminal.rawPrint(MOUSE_OFF)
            raw.close()
        }
    }
}
