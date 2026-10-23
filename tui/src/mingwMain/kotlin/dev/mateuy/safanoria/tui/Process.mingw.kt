package dev.mateuy.safanoria.tui

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv
import platform.posix.system

// system() runs `cmd /c`; its exit code is the command's.
actual fun runHere(directory: String, command: List<String>): Int? =
    system("cd /d ${cmdQuoted(directory)} && " + command.joinToString(" ") { cmdQuoted(it) }).takeIf { it != -1 }

internal actual fun runQuietly(command: List<String>): Boolean =
    system(command.joinToString(" ") { cmdQuoted(it) } + " >nul 2>nul") == 0

internal actual fun installed(program: String): Boolean = system("where ${cmdQuoted(program)} >nul 2>nul") == 0

@OptIn(ExperimentalForeignApi::class)
internal actual fun userShell(): String = getenv("COMSPEC")?.toKString()?.takeIf { it.isNotBlank() } ?: "cmd"
