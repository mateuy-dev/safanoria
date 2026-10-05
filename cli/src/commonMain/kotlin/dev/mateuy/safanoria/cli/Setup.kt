package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.CliktCommand
import dev.mateuy.safanoria.core.FileAction
import dev.mateuy.safanoria.core.FileChange
import dev.mateuy.safanoria.core.Install
import dev.mateuy.safanoria.core.SystemFileSystem
import okio.Path

/**
 * Shared by `init` and `update`. Writes what [changes] say: missing and managed files directly; a
 * project's file that differs only when [yes] or the user agrees ([prompts]; null when nothing
 * may be asked). Prints one line per file.
 */
internal fun CliktCommand.applyChanges(root: Path, changes: List<FileChange>, yes: Boolean, dryRun: Boolean, prompts: Prompts?) {
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
                val settings = shown == Install.SETTINGS_FILE
                val why = when {
                    claude -> "doesn't point agents to the safanoria skill"
                    settings -> "has no SessionStart hook that gives sessions their ticket"
                    else -> "differs from Safanoria's"
                }
                val what = when {
                    claude -> "add the Safanoria paragraph to it"
                    settings -> "add the hook to it"
                    else -> "replace it with Safanoria's"
                }
                val answer = when {
                    yes -> true
                    dryRun -> false
                    else -> prompts?.confirm("$shown $why. ${what.replaceFirstChar { it.uppercase() }}?", default = false)
                }
                when (answer) {
                    true -> { write(); echo("${would}${if (claude || settings) "update" else "replace"} $shown") }
                    false -> echo("${if (dryRun) "would ask about" else "kept"} $shown: it $why (--yes to $what)")
                    null -> echo("kept $shown: it $why, and there is no terminal to ask (--yes to $what)")
                }
            }
        }
    }
}
