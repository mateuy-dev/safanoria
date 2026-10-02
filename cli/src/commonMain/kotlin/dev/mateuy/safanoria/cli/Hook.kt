package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import dev.mateuy.safanoria.core.GitException
import dev.mateuy.safanoria.core.HookState
import dev.mateuy.safanoria.core.Hooks
import okio.Path

/** `safanoria hook install|uninstall`: the git pre-commit hook that validates staged tickets. */
class Hook : CliktCommand(name = "hook") {
    override fun help(context: Context) =
        "The git pre-commit hook that runs `${Hooks.COMMAND}`. Hooks aren't committed: each clone installs it."
    override fun run() = Unit
}

/** Shared by install and uninstall: where the hook goes. */
abstract class HookCommand(name: String) : RepositoryCommand(name) {
    protected val dryRun by option("--dry-run", help = "Show what would change, write nothing").flag()

    protected fun hookPath(): Path = try {
        Hooks.path(repository.git)
    } catch (e: GitException) {
        throw PrintMessage("Not a git repository, or git is missing: ${e.message}", 1, true)
    }
}

class HookInstall : HookCommand("install") {
    override fun help(context: Context) =
        "Install the pre-commit hook. A pre-commit hook that isn't Safanoria's is left alone."

    override fun run() {
        val path = hookPath()
        when (Hooks.state(repository, path)) {
            HookState.SAFANORIA -> echo("already installed: $path")
            HookState.FOREIGN -> throw PrintMessage(
                "$path is another tool's hook; not changed. Add this line to it (or to your hook manager):\n  ${Hooks.COMMAND}",
                1, true,
            )
            HookState.NONE -> {
                if (!dryRun) Hooks.install(repository, path)
                echo("${if (dryRun) "would install" else "installed"} $path: runs `${Hooks.COMMAND}` before each commit")
            }
        }
    }
}

class HookUninstall : HookCommand("uninstall") {
    override fun help(context: Context) = "Remove the pre-commit hook, if it is Safanoria's."

    override fun run() {
        val path = hookPath()
        when (Hooks.state(repository, path)) {
            HookState.NONE -> echo("not installed")
            HookState.FOREIGN -> throw PrintMessage("$path is another tool's hook; not removed", 1, true)
            HookState.SAFANORIA -> {
                if (!dryRun) repository.fileSystem.delete(path)
                echo("${if (dryRun) "would remove" else "removed"} $path")
            }
        }
    }
}

fun hookCommand(): CliktCommand = Hook().subcommands(HookInstall(), HookUninstall())
