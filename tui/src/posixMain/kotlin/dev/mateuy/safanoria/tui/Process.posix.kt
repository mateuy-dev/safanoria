package dev.mateuy.safanoria.tui

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv
import platform.posix.system

// system() runs `sh -c`, ignoring Ctrl-C here while the command has the terminal.
internal actual fun runHere(directory: String, command: List<String>): Int? =
    exitCode(system("cd ${shellQuoted(directory)} && exec " + command.joinToString(" ") { shellQuoted(it) }))

internal actual fun runQuietly(command: List<String>): Boolean =
    exitCode(system(command.joinToString(" ") { shellQuoted(it) } + " >/dev/null 2>&1")) == 0

internal actual fun installed(program: String): Boolean =
    exitCode(system("command -v ${shellQuoted(program)} >/dev/null 2>&1")) == 0

@OptIn(ExperimentalForeignApi::class)
internal actual fun userShell(): String = getenv("SHELL")?.toKString()?.takeIf { it.isNotBlank() } ?: "sh"

// The wait status: exited normally when the low 7 bits are 0, with the code in the next byte.
private fun exitCode(status: Int): Int? = if (status != -1 && (status and 0x7F) == 0) (status shr 8) and 0xFF else null
