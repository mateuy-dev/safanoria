package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import dev.mateuy.safanoria.core.CONFIG_FILE
import dev.mateuy.safanoria.core.ComponentSpec
import dev.mateuy.safanoria.core.FileAction
import dev.mateuy.safanoria.core.FileChange
import dev.mateuy.safanoria.core.Install
import dev.mateuy.safanoria.core.Repository
import dev.mateuy.safanoria.core.SystemFileSystem
import dev.mateuy.safanoria.core.Validator
import dev.mateuy.safanoria.core.VersionSource

/** `safanoria init`: sets up Safanoria in a project (README "Adding Safanoria to a project"). */
class Init : CliktCommand(name = "init") {
    override fun help(context: Context) =
        "Set up Safanoria here (or in --root): safanoria.yaml, the ticket directory with templates, " +
            "the agent skill and SPEC.md, and the CLAUDE.md paragraph. Asks for the components unless given."

    private val cli by requireObject<CliContext>()
    private val dir by option("--dir", help = "Ticket directory").default("tickets")
    private val mainBranch by option("--main-branch", help = "Branch releases are made from").default("main")
    private val components by option("--component", help = "NAME=FILE:PROPERTY, e.g. app=gradle.properties:version (repeatable)").multiple()
    private val externals by option("--external", help = "Component released from another repository (repeatable)").multiple()
    private val yes by option("--yes", help = "Change the project's existing files (e.g. CLAUDE.md) without asking").flag()
    private val dryRun by option("--dry-run", help = "Show what would be written, write nothing").flag()

    override fun run() {
        val root = cli.projectRoot
        if (SystemFileSystem.exists(root / CONFIG_FILE)) {
            throw PrintMessage("$CONFIG_FILE already exists in $root: use `safanoria update`", 1, true)
        }
        if (dir.startsWith("/") || ".." in dir.split('/')) throw usage("--dir must be a path inside the project", "--dir")
        val specs = components.map(::parseComponent) + externals.map { ComponentSpec(checkName(it, "--external"), null) }
        val chosen = specs.ifEmpty { askComponents() }
        chosen.groupBy { it.name }.filterValues { it.size > 1 }.keys.firstOrNull()?.let { throw usage("component '$it' given twice") }

        val config = FileChange(root / CONFIG_FILE, Install.config(dir, chosen, mainBranch), FileAction.CREATE, managed = false)
        applyChanges(root, listOf(config) + Install.plan(SystemFileSystem, root, dir), yes, dryRun)
        if (dryRun) return

        val problems = Validator(Repository(root)).validate()
        if (problems.isNotEmpty()) {
            problems.forEach { echo(it.toString(), err = true) }
            throw ProgramResult(1)
        }
        echo("Safanoria is set up. Next: `safanoria new \"<title>\"`, and add `safanoria release <component>` to your release process.")
    }

    /** A usage error shown with this command's usage, not the root's (thrown from run(), Clikt has no context for it). */
    private fun usage(message: String, param: String? = null) = UsageError(message, param).apply { context = currentContext }

    private fun checkName(name: String, option: String): String {
        if (!Regex("^[a-z][a-z0-9-]*$").matches(name)) throw usage("'$name' is not a component name: lowercase letters, digits and '-'", option)
        return name
    }

    private fun parseComponent(text: String): ComponentSpec {
        val name = text.substringBefore('=', "")
        val source = text.substringAfter('=', "")
        val file = source.substringBeforeLast(':', "")
        val property = source.substringAfterLast(':', "")
        if (name.isEmpty() || file.isEmpty() || property.isEmpty()) {
            throw usage("'$text' must be NAME=FILE:PROPERTY, e.g. app=gradle.properties:version", "--component")
        }
        return ComponentSpec(checkName(name, "--component"), VersionSource.Property(file, property))
    }

    /** Asks on the terminal: names, then each one's version source. */
    private fun askComponents(): List<ComponentSpec> {
        if (!interactive) throw usage("give the components with --component NAME=FILE:PROPERTY or --external NAME (no terminal to ask)")
        echo("Components are the parts of the project released with their own version (e.g. app, server).")
        val names = ask("Components, comma-separated", "app").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (names.isEmpty()) throw usage("at least one component is needed")
        return names.map { name ->
            checkName(name, "component")
            val answer = ask("Where is $name's version? FILE:PROPERTY, or 'external' if released from another repository", "gradle.properties:version").orEmpty()
            if (answer == "external") ComponentSpec(name, null) else parseComponent("$name=$answer")
        }
    }
}
