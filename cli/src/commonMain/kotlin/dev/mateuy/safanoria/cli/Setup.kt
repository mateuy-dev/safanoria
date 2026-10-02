package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.mordant.terminal.StringPrompt
import com.github.ajalt.mordant.terminal.YesNoPrompt
import dev.mateuy.safanoria.core.FileAction
import dev.mateuy.safanoria.core.FileChange
import dev.mateuy.safanoria.core.Install
import dev.mateuy.safanoria.core.SystemFileSystem
import okio.Path

/** Shared by `init` and `update`: asking on the terminal and writing [FileChange]s. */
internal val CliktCommand.interactive: Boolean get() = terminal.terminalInfo.inputInteractive

/** A yes/no answer (default no); null without a terminal to ask. */
internal fun CliktCommand.confirm(question: String): Boolean? =
    if (!interactive) null else YesNoPrompt(question, terminal, default = false).ask() ?: false

/** A line of input, [default] when empty; null without a terminal. */
internal fun CliktCommand.ask(question: String, default: String? = null): String? =
    if (!interactive) null else StringPrompt(question, terminal, default = default).ask()?.trim()

/**
 * Writes what [changes] say: missing and managed files directly; a project's file that differs
 * only when [yes] or the user agrees. Prints one line per file.
 */
internal fun CliktCommand.applyChanges(root: Path, changes: List<FileChange>, yes: Boolean, dryRun: Boolean) {
    val would = if (dryRun) "would " else ""
    for (c in changes) {
        val shown = c.path.relativeTo(root).segments.joinToString("/")
        fun write() {
            if (dryRun) return
            c.path.parent?.let { SystemFileSystem.createDirectories(it) }
            SystemFileSystem.write(c.path) { writeUtf8(c.text) }
        }
        when (c.action) {
            FileAction.SAME -> echo("unchanged $shown")
            FileAction.CREATE -> { write(); echo("${would}create $shown") }
            FileAction.REPLACE -> { write(); echo("${would}replace $shown") }
            FileAction.DIFFERS -> {
                val claude = c.path.name == Install.CLAUDE_FILE
                val why = if (claude) "doesn't point agents to the safanoria skill" else "differs from Safanoria's"
                val what = if (claude) "add the Safanoria paragraph to it" else "replace it with Safanoria's"
                val answer = when {
                    yes -> true
                    dryRun -> false
                    else -> confirm("$shown $why. ${what.replaceFirstChar { it.uppercase() }}?")
                }
                when (answer) {
                    true -> { write(); echo("${would}${if (claude) "update" else "replace"} $shown") }
                    false -> echo("${if (dryRun) "would ask about" else "kept"} $shown: it $why (--yes to $what)")
                    null -> echo("kept $shown: it $why, and there is no terminal to ask (--yes to $what)")
                }
            }
        }
    }
}
