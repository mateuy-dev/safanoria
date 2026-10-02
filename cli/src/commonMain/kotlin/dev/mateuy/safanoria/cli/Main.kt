package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.PrintMessage
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

/** Shared by all commands: finds the repository once, from `--root` or the working directory. */
class CliContext(private val rootOption: String?) {
    val repository: Repository by lazy {
        Repository.find(rootOption?.toPath())
            ?: throw PrintMessage(
                "Not in a Safanoria repository: no safanoria.yaml in ${rootOption ?: "the working directory"} or above.",
                statusCode = 2,
                printError = true,
            )
    }
}

class Safanoria : CliktCommand(name = "safanoria") {
    private val root by option("--root", help = "Repository root (default: the nearest directory with safanoria.yaml)")
    private val context by findOrSetObject { CliContext(root) }

    override fun help(context: Context) = "Tickets as markdown files in your repository (SPEC.md)."
    override fun run() {
        context // create it with --root before subcommands run
    }
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
}

/** The command tree; tests run it with Clikt's `test()`. */
fun cli(): CliktCommand = Safanoria().subcommands(New(), Validate(), Version(), Dump())

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
