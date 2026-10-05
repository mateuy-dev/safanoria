package dev.mateuy.safanoria.gui.data

import okio.Path
import java.io.File

/** Opens a terminal window for the user to work in a directory. */
fun interface Terminal {
    /** Opens a terminal in [directory]. Returns null when launched, else why not. */
    fun open(directory: Path): String?
}

/**
 * The desktop's terminal. On Linux there is no single one: `$TERMINAL` when set, else the first
 * of the usual ones found on the PATH. They start in the launching process's working directory,
 * except those that hand the window to a running server and need to be told.
 */
class SystemTerminal(
    private val os: String = System.getProperty("os.name"),
    private val environment: (String) -> String? = System::getenv,
) : Terminal {
    override fun open(directory: Path): String? {
        val dir = File(directory.toString())
        if (!dir.isDirectory) return "no such directory: $directory"
        val command = command(directory.toString()) ?: return "no terminal found: set TERMINAL to the command that opens yours"
        return try {
            ProcessBuilder(command).directory(dir)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            null
        } catch (e: Exception) {
            "couldn't run ${command.first()}: ${e.message}"
        }
    }

    internal fun command(directory: String): List<String>? = when {
        os.startsWith("Mac") -> listOf("open", "-a", "Terminal", directory)
        os.startsWith("Windows") -> listOf("cmd", "/c", "start", "cmd")
        else -> (listOfNotNull(environment("TERMINAL")?.takeIf { it.isNotBlank() }) + LINUX_TERMINALS)
            .firstOrNull(::onPath)
            ?.let { terminal -> listOf(terminal) + directoryOption(terminal, directory) }
    }

    private fun directoryOption(terminal: String, directory: String): List<String> = when (File(terminal).name) {
        "gnome-terminal", "kgx", "ptyxis", "xfce4-terminal" -> listOf("--working-directory=$directory")
        "konsole" -> listOf("--workdir", directory)
        else -> emptyList()
    }

    private fun onPath(command: String): Boolean =
        if (File(command).isAbsolute) File(command).canExecute()
        else environment("PATH").orEmpty().split(File.pathSeparator).any { File(it, command).canExecute() }

    private companion object {
        val LINUX_TERMINALS = listOf(
            "x-terminal-emulator", "gnome-terminal", "ptyxis", "kgx", "konsole", "xfce4-terminal", "kitty", "alacritty", "foot", "xterm",
        )
    }
}
