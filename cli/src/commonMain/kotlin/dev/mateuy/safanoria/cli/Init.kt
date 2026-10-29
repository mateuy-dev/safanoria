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
import com.github.ajalt.clikt.parameters.options.nullableFlag
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

/** `safanoria-cli init`: sets up Safanoria in a project (README "Adding Safanoria to a project"). */
class Init : CliktCommand(name = "init") {
    override fun help(context: Context) =
        "Set up Safanoria here (or in --root): safanoria.yaml, the ticket directory with templates, " +
            "the CLAUDE.md and AGENTS.md instructions and the SessionStart hook in .claude/settings.json; and the agent skill and SPEC.md in " +
            "~/.claude/skills/safanoria/ and ~/.agents/skills/safanoria/, for all your projects. Asks for the components unless given, and whether the project " +
            "also gets its own copy of the skill."

    private val cli by requireObject<CliContext>()
    private val dir by option("--dir", help = "Ticket directory").default("tickets")
    private val mainBranch by option("--main-branch", help = "Branch releases are made from").default("main")
    private val worktree by option("--worktree", help = "Where a ticket's worktree goes, with {id} for the ticket id (default: ../<this directory>--{id})")
    private val components by option("--component", help = "NAME=FILE:PROPERTY, e.g. app=gradle.properties:version (repeatable)").multiple()
    private val externals by option("--external", help = "Component released from another repository (repeatable)").multiple()
    private val projectSkill by option(
        "--project-skill",
        help = "Also put the skill and SPEC.md in the project (.claude/skills/safanoria/ and .agents/skills/safanoria/, to commit), for Claude Code on the web " +
            "and contributors without safanoria-cli",
    ).nullableFlag("--no-project-skill")
    private val yes by option("--yes", help = "Change the project's existing files (e.g. CLAUDE.md) without asking").flag()
    private val dryRun by option("--dry-run", help = "Show what would be written, write nothing").flag()

    override fun run() {
        val root = cli.projectRoot
        if (SystemFileSystem.exists(root / CONFIG_FILE)) {
            throw PrintMessage("$CONFIG_FILE already exists in $root: use `safanoria-cli update`", 1, true)
        }
        if (dir.startsWith("/") || ".." in dir.split('/')) throw usage("--dir must be a path inside the project", "--dir")
        val worktree = worktree ?: "../${root.name}--{id}"
        if ("{id}" !in worktree) throw usage("--worktree must contain {id}, replaced by the ticket id", "--worktree")
        val specs = components.map(::parseComponent) + externals.map { ComponentSpec(checkName(it, "--external"), null) }
        val chosen = specs.ifEmpty { askComponents() }
        chosen.groupBy { it.name }.filterValues { it.size > 1 }.keys.firstOrNull()?.let { throw usage("component '$it' given twice") }
        val claudeDir = cli.claudeDir
        val withProjectSkill = projectSkill ?: cli.prompts?.confirm(
            "Also commit a copy of the skill and SPEC.md in the project (${Install.SKILL_DIR}/ and ${Install.CODEX_SKILL_DIR}/)? Claude Code on the web " +
                "and contributors without safanoria-cli need it; you would update it with `safanoria-cli update` on $mainBranch.",
            default = false,
        ) ?: false
        if (claudeDir == null && !withProjectSkill) {
            throw usage("no home directory to install the skill in (HOME, CLAUDE_CONFIG_DIR): use --project-skill")
        }

        val config = FileChange(root / CONFIG_FILE, Install.config(dir, chosen, worktree, mainBranch), FileAction.CREATE, managed = false)
        val global = claudeDir?.let { Install.globalPlan(SystemFileSystem, it) }.orEmpty() +
            cli.agentsDir?.let { Install.codexGlobalPlan(SystemFileSystem, it) }.orEmpty()
        applyChanges(root, listOf(config) + global + Install.plan(SystemFileSystem, root, dir, withProjectSkill), yes, dryRun, cli.prompts)
        if (dryRun) return

        val problems = Validator(Repository(root)).validate()
        if (problems.isNotEmpty()) {
            problems.forEach { echo(it.toString(), err = true) }
            throw ProgramResult(1)
        }
        echo(
            "Safanoria is set up. Next: `safanoria-cli new \"<title>\"`; `safanoria-cli hook install` to validate tickets before " +
                "each commit; and `safanoria-cli release <component>` in your release process.",
        )
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

    /** Asks for the names, then for each one where its version is. */
    private fun askComponents(): List<ComponentSpec> {
        val prompts = cli.prompts
            ?: throw usage("give the components with --component NAME=FILE:PROPERTY or --external NAME (no terminal to ask)")
        echo("Components are the parts of the project released with their own version (e.g. app, server).")
        val names = prompts.text("Components, comma-separated", "app").split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (names.isEmpty()) throw usage("at least one component is needed")
        return names.map { name ->
            checkName(name, "component")
            val where = prompts.choose(
                "Where is $name's version?",
                listOf(
                    Choice("file", "a file in this repository", "e.g. gradle.properties, property version"),
                    Choice("external", "another repository", "an external component: the version is given when releasing"),
                ),
            )
            if (where == "external") ComponentSpec(name, null)
            else parseComponent("$name=${prompts.text("$name's version, as FILE:PROPERTY", "gradle.properties:version").trim()}")
        }
    }
}
