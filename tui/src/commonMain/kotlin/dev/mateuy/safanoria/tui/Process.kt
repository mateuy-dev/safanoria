package dev.mateuy.safanoria.tui

/**
 * Runs [command] (a program and its arguments) in [directory] on this terminal, with its input
 * and output, and waits for it to end. Returns its exit code, or null when it couldn't start.
 * The CLI uses it too, for `safanoria-cli diff`.
 */
expect fun runHere(directory: String, command: List<String>): Int?

/** Runs [command] without its output, as for `tmux new-window`; true when it succeeded. */
internal expect fun runQuietly(command: List<String>): Boolean

/** Whether [program] is on the PATH. */
internal expect fun installed(program: String): Boolean

/** The user's shell: `$SHELL`, else `sh` (`%COMSPEC%`, else `cmd`, on Windows). */
internal expect fun userShell(): String

/** [arg] for a POSIX shell: in single quotes, unless it is plain. */
internal fun shellQuoted(arg: String): String =
    if (arg.isNotEmpty() && arg.all { it.isLetterOrDigit() || it in "-_./=:@%+," }) arg else "'" + arg.replace("'", "'\\''") + "'"

/** [arg] for `cmd.exe`: in double quotes, unless it is plain. */
internal fun cmdQuoted(arg: String): String =
    if (arg.isNotEmpty() && arg.all { it.isLetterOrDigit() || it in "-_./=:@\\," }) arg else "\"" + arg.replace("\"", "\"\"") + "\""
