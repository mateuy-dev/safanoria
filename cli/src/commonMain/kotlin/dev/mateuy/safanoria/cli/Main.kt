package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.core.findOrSetObject
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.terminal.Terminal
import dev.mateuy.safanoria.core.Embedded
import dev.mateuy.safanoria.core.Install
import dev.mateuy.safanoria.core.Repository
import dev.mateuy.safanoria.core.SPEC_VERSION
import dev.mateuy.safanoria.tui.runTui
import okio.Path
import okio.Path.Companion.toPath

/**
 * Shared by all commands: finds the repository once, from `--root` or the working directory, and
 * holds the [prompts] (null when stdin or stdout isn't a terminal: then nothing is asked) and
 * Claude Code's user directory, where the skill is installed ([claudeDir], null when unknown).
 */
class CliContext(val rootOption: String?, val prompts: Prompts?, val claudeDir: Path? = Install.claudeDir()) {
    /** Where `init` sets up a project: `--root` or the working directory. */
    val projectRoot: okio.Path by lazy {
        dev.mateuy.safanoria.core.SystemFileSystem.canonicalize((rootOption ?: ".").toPath())
    }

    /** The repository, or null when there is none (yet: `init`). */
    val repositoryOrNull: Repository? by lazy { Repository.find(rootOption?.toPath()) }

    val repository: Repository by lazy {
        repositoryOrNull
            ?: throw PrintMessage(
                "Not in a Safanoria repository: no safanoria.yaml in ${rootOption ?: "the working directory"} or above.",
                statusCode = 2,
                printError = true,
            )
    }
}

/**
 * The root command. [prompts] replaces the terminal's (tests); by default they exist only on a
 * terminal. [tui] runs the terminal app on a project's root (tests record it instead). [claudeDir]
 * replaces Claude Code's user directory (tests).
 */
class Safanoria(
    private val prompts: Prompts? = null,
    private val tui: (Terminal, Path) -> Unit = ::runTui,
    private val claudeDir: Path? = null,
) : CliktCommand(name = "safanoria-cli") {
    private val root by option("--root", help = "Repository root (default: the nearest directory with safanoria.yaml)")
    private val context by findOrSetObject { CliContext(root, prompts ?: terminalPrompts(), claudeDir ?: Install.claudeDir()) }

    override val invokeWithoutSubcommand = true

    override fun help(context: Context) =
        "Tickets as markdown files in your repository (SPEC.md). Without a command, on a terminal, opens the terminal app " +
            "(as `tui`), or outside a project offers to set one up. " +
            "The desktop app is `safanoria`."

    override fun helpEpilog(context: Context) =
        "To land in a started ticket's directory, add this function to ~/.bashrc or ~/.zshrc and start tickets with " +
            "safanoria-start <id> (a program can't change its shell's directory):\n\n" +
            SHELL_FUNCTION.joinToString("\u0085")

    override fun run() {
        val cli = context // create it with --root before subcommands run
        if (currentContext.invokedSubcommand != null) return
        val prompts = cli.prompts ?: throw PrintHelpMessage(currentContext, error = true)
        // In a project, the terminal app; outside one, only `init` can run.
        cli.repositoryOrNull?.let { return tui(currentContext.terminal, it.root) }
        val name = prompts.choose("What do you want to do?", listOf(Choice("init", description = "Set up Safanoria in this project")))
        // Parsed again with the command: a subcommand parsed on its own would have no parent context (--root, prompts).
        parse(root?.let { listOf("--root", it) }.orEmpty() + name)
    }

    private fun terminalPrompts(): Prompts? {
        val info = currentContext.terminal.terminalInfo
        return if (info.inputInteractive && info.outputInteractive) TerminalPrompts(currentContext.terminal) else null
    }
}

class Version : CliktCommand(name = "version") {
    override fun help(context: Context) = "Print the tool version and the spec version it implements."
    override fun run() = echo("safanoria-cli ${Embedded.VERSION} (spec $SPEC_VERSION)")
}

/** A path as shown to the user: relative to the working directory when it's under it. */
fun displayPath(path: Path): String {
    val cwd = dev.mateuy.safanoria.core.SystemFileSystem.canonicalize(".".toPath())
    return runCatching { path.relativeTo(cwd) }.getOrNull()?.toString()?.takeIf { !it.startsWith("..") } ?: path.toString()
}

/** Base for commands that work on a repository. */
abstract class RepositoryCommand(name: String) : CliktCommand(name = name) {
    private val cli by requireObject<CliContext>()
    protected val repository: Repository get() = cli.repository
    /** Null when nothing may be asked: see [Prompts]. */
    internal val prompts: Prompts? get() = cli.prompts
    /** Claude Code's user directory, where the skill is installed; null when unknown. */
    protected val claudeDir: Path? get() = cli.claudeDir
}

/**
 * The command tree; tests run it with Clikt's `test()`, and give it [prompts] to answer questions, a
 * [tui] to record it and a [claudeDir] instead of the user's.
 */
fun cli(prompts: Prompts? = null, tui: (Terminal, Path) -> Unit = ::runTui, claudeDir: Path? = null): CliktCommand =
    Safanoria(prompts, tui, claudeDir).subcommands(Init(), Update(), New(), ListTickets(), BoardCommand(), ReleaseCommand(), NotesCommand(), StartCommand(), FinishCommand(), MergeCommand(), ReopenCommand(), ReviewCommand(), DiffCommand(), ContextCommand(), Validate(), TuiCommand(), hookCommand(), Version(), Dump())

/**
 * Like Clikt's `main`, but usage errors (bad option, missing argument) exit 2 as documented
 * (Clikt uses 1, which here means "problems found" or "refused").
 */
fun main(args: Array<String>) {
    val command = cli()
    try {
        command.parse(args)
    } catch (e: CliktError) {
        command.echoFormattedHelp(e)
        command.currentContext.exitProcess(if (e is UsageError) 2 else e.statusCode)
    }
}
