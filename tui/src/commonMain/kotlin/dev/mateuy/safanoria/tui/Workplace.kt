package dev.mateuy.safanoria.tui

import dev.mateuy.safanoria.core.Herdr
import dev.mateuy.safanoria.core.HerdrException
import dev.mateuy.safanoria.core.Size
import dev.mateuy.safanoria.core.environmentVariable
import dev.mateuy.safanoria.views.Notice
import okio.Path

/**
 * Where the user works on a ticket from the terminal app, which may run without a desktop (over
 * SSH): in Herdr a tab, in tmux a window, and otherwise right here, with the app back when the
 * shell (or Claude Code) ends.
 */
internal enum class Workplace(val text: String) {
    HERDR("in a new Herdr tab"),
    TMUX("in a new tmux window"),
    HERE("in this terminal (exit it to come back)");

    companion object {
        fun current(): Workplace = when {
            Herdr.inside() -> HERDR
            !environmentVariable("TMUX").isNullOrEmpty() -> TMUX
            else -> HERE
        }
    }
}

internal const val CLAUDE = "claude"

/**
 * Opens [dir], the worktree of ticket [id], to work in: a shell, or Claude Code when [claude], with
 * no first prompt (the SessionStart hook gives it the ticket; it waits for the user). In Herdr, Claude on a large ticket (L, XL) gets a workspace
 * of its own, as `safanoria-cli start --herdr` does. Returns what to tell the user, after [done].
 */
internal suspend fun Navigator.work(dir: Path, id: String, claude: Boolean, size: Size?, done: String? = null): Notice? {
    // Named after the ticket: Claude Code shows the session's name as the terminal's title.
    val command = if (claude) listOf(CLAUDE, "--name", id) else null
    fun told(text: String?, error: Boolean = false) = listOfNotNull(done, text).joinToString(". ").ifEmpty { null }?.let { Notice(it, error) }
    return when (Workplace.current()) {
        Workplace.HERDR -> try {
            told(Herdr().open(dir, id, prompt = null, workspace = claude && size in setOf(Size.L, Size.XL), claude = claude))
        } catch (e: HerdrException) {
            told("Not opened in Herdr: ${e.message}", error = true)
        }
        Workplace.TMUX ->
            if (runQuietly(listOf("tmux", "new-window", "-c", dir.toString(), "-n", id) + command.orEmpty())) told("opened a tmux window '$id'")
            else told("No tmux window opened: tmux new-window failed", error = true)
        Workplace.HERE -> {
            var exit: Int? = 0
            outside {
                terminal.println("Safanoria · $id: ${if (claude) "Claude Code" else "a shell"} in $dir. Exit it to come back.")
                exit = runHere(dir.toString(), command ?: listOf(userShell()))
            }
            store.refresh()
            if (exit == null) told("Couldn't run ${command?.first() ?: userShell()} in $dir", error = true) else told(null)
        }
    }
}
