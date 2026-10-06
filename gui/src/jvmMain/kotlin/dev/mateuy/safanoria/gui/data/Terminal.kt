package dev.mateuy.safanoria.gui.data

import okio.Path
import java.io.File

/** Opens a terminal window for the user to work in a directory. */
interface Terminal {
    /**
     * Opens a terminal in [directory], running [command] (a program and its arguments) in it when
     * given. Returns null when launched, else why not.
     */
    fun open(directory: Path, command: List<String> = emptyList()): String?

    /** Whether [program] is installed, so a terminal can run it. */
    fun has(program: String): Boolean
}

/**
 * The desktop's terminal. On Linux there is no single one: `$TERMINAL` when set, else the first
 * of the usual ones found on the PATH. They start in the launching process's working directory,
 * except those that hand the window to a running server and need to be told. How they take a
 * command to run differs too.
 */
class SystemTerminal(
    private val os: String = System.getProperty("os.name"),
    private val environment: (String) -> String? = System::getenv,
) : Terminal {
    override fun has(program: String): Boolean = find(program) != null

    override fun open(directory: Path, command: List<String>): String? {
        val dir = File(directory.toString())
        if (!dir.isDirectory) return "no such directory: $directory"
        if (command.isNotEmpty() && !has(command.first())) return "${command.first()} isn't installed"
        val command = command(directory.toString(), command) ?: return "no terminal found: set TERMINAL to the command that opens yours"
        return try {
            ProcessBuilder(command).directory(dir)
                // The packaged app's launcher marks its own process with this; inherited, it makes
                // any packaged Java app started from that terminal (`safanoria` too) take its
                // arguments as the JVM's.
                .apply { environment().remove("_JPACKAGE_LAUNCHER") }
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            null
        } catch (e: Exception) {
            "couldn't run ${command.first()}: ${e.message}"
        }
    }

    internal fun command(directory: String, run: List<String> = emptyList()): List<String>? {
        // By its full path: the terminal's PATH isn't always this process's.
        val program = run.take(1).map { find(it) ?: it } + run.drop(1)
        return when {
            os.startsWith("Mac") -> when {
                run.isEmpty() -> listOf("open", "-a", "Terminal", directory)
                // Terminal.app takes no command to run: it is typed into the new window's shell.
                else -> (listOf("cd", directory).joinToString(" ", transform = ::shellQuoted) + " && " + program.joinToString(" ", transform = ::shellQuoted))
                    .let { script -> listOf("osascript", "-e", "tell application \"Terminal\" to do script ${appleScriptQuoted(script)}", "-e", "tell application \"Terminal\" to activate") }
            }
            os.startsWith("Windows") -> listOf("cmd", "/c", "start", "cmd") + if (run.isEmpty()) emptyList() else listOf("/k") + program
            else -> (listOfNotNull(environment("TERMINAL")?.takeIf { it.isNotBlank() }) + LINUX_TERMINALS)
                .firstOrNull { find(it) != null }
                ?.let { terminal ->
                    listOf(terminal) + directoryOption(terminal, directory) +
                        // Followed by a shell: the window stays, with whatever the program said, when it exits.
                        if (run.isEmpty()) emptyList() else runOption(terminal) + listOf("sh", "-c", "\"\$@\"; exec \"\${SHELL:-sh}\"", "sh") + program
                }
        }
    }

    /** What goes before a command and its arguments for [terminal] to run them. `-e` is the one most take. */
    private fun runOption(terminal: String): List<String> = when (File(terminal).name) {
        "gnome-terminal", "ptyxis" -> listOf("--")
        "xfce4-terminal" -> listOf("-x")
        "kitty", "foot" -> emptyList()
        else -> listOf("-e")
    }

    private fun directoryOption(terminal: String, directory: String): List<String> = when (File(terminal).name) {
        "gnome-terminal", "kgx", "ptyxis", "xfce4-terminal" -> listOf("--working-directory=$directory")
        "konsole" -> listOf("--workdir", directory)
        else -> emptyList()
    }

    /**
     * The executable [command] names: on the PATH, or in `~/.local/bin`, where Claude Code installs
     * itself and which a desktop session's PATH may lack.
     */
    private fun find(command: String): String? {
        if (File(command).isAbsolute) return command.takeIf { File(it).canExecute() }
        val directories = environment("PATH").orEmpty().split(File.pathSeparator).filter { it.isNotEmpty() } +
            listOfNotNull(environment("HOME")?.let { "$it/.local/bin" })
        val names = if (os.startsWith("Windows")) listOf("$command.exe", "$command.cmd", command) else listOf(command)
        return directories.firstNotNullOfOrNull { dir -> names.map { File(dir, it) }.firstOrNull { it.isFile && it.canExecute() }?.path }
    }

    private fun shellQuoted(text: String) = "'" + text.replace("'", "'\\''") + "'"

    private fun appleScriptQuoted(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private companion object {
        val LINUX_TERMINALS = listOf(
            "x-terminal-emulator", "gnome-terminal", "ptyxis", "kgx", "konsole", "xfce4-terminal", "kitty", "alacritty", "foot", "xterm",
        )
    }
}
