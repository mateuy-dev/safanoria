package dev.mateuy.safanoria.gui.data

import okio.Path
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Starts [command] in [directory] and leaves it running, as for a diff tool that stays open until
 * the user closes it. Returns null when it is running (or ended well) after [wait] milliseconds,
 * else why not: what it printed when it failed by then, e.g. a tool that isn't installed.
 */
fun launch(directory: Path, command: List<String>, wait: Long = 2_000): String? {
    // A file, not a pipe: nobody reads a pipe once this returns, and a full one would block the tool.
    val output = File.createTempFile("safanoria-launch", ".log").apply { deleteOnExit() }
    return try {
        val process = ProcessBuilder(command).directory(File(directory.toString()))
            // As for a terminal (SystemTerminal): inherited, it breaks packaged Java apps it starts, like IntelliJ's launcher.
            .apply { environment().remove("_JPACKAGE_LAUNCHER") }
            .redirectErrorStream(true).redirectOutput(output)
            .start()
        if (!process.waitFor(wait, TimeUnit.MILLISECONDS) || process.exitValue() == 0) null
        else output.readText().trim().lines().takeLast(5).joinToString("\n").ifEmpty { "${command.first()} failed (${process.exitValue()})" }
    } catch (e: Exception) {
        "couldn't run ${command.first()}: ${e.message}"
    }
}
