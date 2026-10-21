package dev.mateuy.safanoria.tui

import java.io.File

private val windows = System.getProperty("os.name").startsWith("Windows")

internal actual fun runHere(directory: String, command: List<String>): Int? = try {
    ProcessBuilder(command).directory(File(directory)).inheritIO().start().waitFor()
} catch (e: Exception) {
    null
}

internal actual fun runQuietly(command: List<String>): Boolean = try {
    ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start().waitFor() == 0
} catch (e: Exception) {
    false
}

internal actual fun installed(program: String): Boolean =
    runQuietly(if (windows) listOf("where", program) else listOf("sh", "-c", "command -v ${shellQuoted(program)}"))

internal actual fun userShell(): String =
    (if (windows) System.getenv("COMSPEC") else System.getenv("SHELL"))?.takeIf { it.isNotBlank() } ?: if (windows) "cmd" else "sh"
