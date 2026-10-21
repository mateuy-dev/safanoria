package dev.mateuy.safanoria.tui

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.MouseTracking
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.value
import platform.posix.read

// Mordant sets raw mode (with reads that give up after 100 ms); the bytes are parsed here.
internal actual fun openInput(terminal: Terminal): Input {
    val raw = terminal.enterRawMode(MouseTracking.Off)
    terminal.rawPrint(MOUSE_ON)
    val parser = InputParser(::readByte)
    return object : Input {
        override fun read(): InputEvent? = parser.read()

        override fun close() {
            terminal.rawPrint(MOUSE_OFF)
            raw.close()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun readByte(): Int? = memScoped {
    val b = alloc<UByteVar>()
    if (read(0, b.ptr, 1u) == 1L) b.value.toInt() else null
}
