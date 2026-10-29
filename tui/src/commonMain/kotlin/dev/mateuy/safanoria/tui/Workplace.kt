package dev.mateuy.safanoria.tui

import dev.mateuy.safanoria.core.Herdr
import dev.mateuy.safanoria.core.HerdrException
import dev.mateuy.safanoria.core.SessionAgent
import dev.mateuy.safanoria.core.Size
import dev.mateuy.safanoria.core.environmentVariable
import dev.mateuy.safanoria.views.Notice
import okio.Path

/**
 * Where the user works on a ticket from the terminal app, which may run without a desktop (over
 * SSH): in Herdr a tab, in tmux a window, and otherwise right here, with the app back when the
 * shell (or agent) ends.
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

/** Process boundary shared by Herdr, tmux and local sessions; replaceable in page tests. */
internal open class WorkplaceLauncher {
    open fun has(program: String): Boolean = installed(program)
    open fun current(): Workplace = Workplace.current()
    open fun herdr(dir: Path, id: String, agent: SessionAgent?, size: Size?): String =
        Herdr().open(dir, id, prompt = null, workspace = agent != null && size in setOf(Size.L, Size.XL), agent = agent)
    open fun quiet(command: List<String>): Boolean = runQuietly(command)
    open fun here(dir: Path, command: List<String>): Int? = runHere(dir.toString(), command)
}

/**
 * Opens [dir], the worktree of ticket [id], to work in: a shell, or the selected [agent], with
 * no first prompt (the installed instructions supply ticket context). In Herdr, an agent on a large ticket (L, XL) gets a workspace
 * of its own, as `safanoria-cli start --herdr` does. Returns what to tell the user, after [done].
 */
internal suspend fun Navigator.work(dir: Path, id: String, agent: SessionAgent?, size: Size?, done: String? = null): Notice? {
    val command = agent?.let { listOf(it.option) + it.arguments(id, prompt = null) }
    fun told(text: String?, error: Boolean = false) = listOfNotNull(done, text).joinToString(". ").ifEmpty { null }?.let { Notice(it, error) }
    return when (workplace.current()) {
        Workplace.HERDR -> try {
            told(workplace.herdr(dir, id, agent, size))
        } catch (e: HerdrException) {
            told("Not opened in Herdr: ${e.message}", error = true)
        }
        Workplace.TMUX ->
            if (workplace.quiet(listOf("tmux", "new-window", "-c", dir.toString(), "-n", id) + command?.let { listOf(it.joinToString(" ", transform = ::shellQuoted)) }.orEmpty())) told("opened a tmux window '$id'")
            else told("No tmux window opened${agent?.let { " for ${it.label}" }.orEmpty()}: tmux new-window failed", error = true)
        Workplace.HERE -> {
            var exit: Int? = 0
            outside {
                terminal.println("Safanoria · $id: ${agent?.label ?: "a shell"} in $dir. Exit it to come back.")
                exit = workplace.here(dir, command ?: listOf(userShell()))
            }
            store.refresh()
            when (exit) {
                null -> told("Couldn't run ${agent?.label ?: userShell()} in $dir", error = true)
                0 -> told(null)
                else -> told("${agent?.label ?: "Shell"} exited with status $exit in $dir", error = true)
            }
        }
    }
}
