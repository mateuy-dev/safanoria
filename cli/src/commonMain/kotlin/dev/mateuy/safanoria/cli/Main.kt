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
import dev.mateuy.safanoria.core.Embedded
import dev.mateuy.safanoria.core.Repository
import dev.mateuy.safanoria.core.SPEC_VERSION
import okio.Path
import okio.Path.Companion.toPath

/**
 * Shared by all commands: finds the repository once, from `--root` or the working directory, and
 * holds the [prompts] (null when stdin or stdout isn't a terminal: then nothing is asked).
 */
class CliContext(val rootOption: String?, val prompts: Prompts?) {
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

/** The root command. [prompts] replaces the terminal's (tests); by default they exist only on a terminal. */
class Safanoria(private val prompts: Prompts? = null) : CliktCommand(name = "safanoria") {
    private val root by option("--root", help = "Repository root (default: the nearest directory with safanoria.yaml)")
    private val context by findOrSetObject { CliContext(root, prompts ?: terminalPrompts()) }

    override val invokeWithoutSubcommand = true

    override fun help(context: Context) =
        "Tickets as markdown files in your repository (SPEC.md). Without a command, on a terminal, asks which one to run."

    override fun helpEpilog(context: Context) =
        "To land in a started ticket's directory, add this function to ~/.bashrc or ~/.zshrc and start tickets with " +
            "safanoria-start <id> (a program can't change its shell's directory):\n\n" +
            SHELL_FUNCTION.joinToString("\u0085")

    override fun run() {
        val cli = context // create it with --root before subcommands run
        if (currentContext.invokedSubcommand != null) return
        val prompts = cli.prompts ?: throw PrintHelpMessage(currentContext, error = true)
        val actions = actions(cli.repositoryOrNull != null)
        val name = prompts.choose("What do you want to do?", actions.map { Choice(it.first, description = it.second) })
        // Parsed again with the command: a subcommand parsed on its own would have no parent context (--root, prompts).
        parse(root?.let { listOf("--root", it) }.orEmpty() + name)
    }

    private fun terminalPrompts(): Prompts? {
        val info = currentContext.terminal.terminalInfo
        return if (info.inputInteractive && info.outputInteractive) TerminalPrompts(currentContext.terminal) else null
    }

    /** The commands a person runs by hand, with what they do; outside a repository, only `init`. */
    private fun actions(inRepository: Boolean): List<Pair<String, String>> =
        if (!inRepository) listOf("init" to "Set up Safanoria in this project")
        else listOf(
            "board" to "Show the board",
            "list" to "List tickets",
            "new" to "Create a ticket",
            "start" to "Start a ticket: its branch and worktree",
            "finish" to "Finish a ticket: review, or done once merged",
            "validate" to "Check the tickets",
            "release" to "Stamp released tickets with a version",
            "update" to "Update the skill and the hooks to this version",
        )
}

class Version : CliktCommand(name = "version") {
    override fun help(context: Context) = "Print the tool version and the spec version it implements."
    override fun run() = echo("safanoria ${Embedded.VERSION} (spec $SPEC_VERSION)")
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
}

/** The command tree; tests run it with Clikt's `test()`, and give it [prompts] to answer questions. */
fun cli(prompts: Prompts? = null): CliktCommand = Safanoria(prompts).subcommands(Init(), Update(), New(), ListTickets(), BoardCommand(), ReleaseCommand(), StartCommand(), FinishCommand(), ContextCommand(), Validate(), hookCommand(), Version(), Dump())

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
