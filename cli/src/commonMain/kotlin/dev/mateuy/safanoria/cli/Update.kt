package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import dev.mateuy.safanoria.core.Embedded
import dev.mateuy.safanoria.core.FileAction
import dev.mateuy.safanoria.core.Install
import dev.mateuy.safanoria.core.SystemFileSystem

/** `safanoria-cli update`: installs this version's skill and spec; offers its templates. */
class Update : RepositoryCommand(name = "update") {
    override fun help(context: Context) =
        "Replace the installed skill and SPEC.md with this version's, and add missing templates. " +
            "Templates, the ticket README, CLAUDE.md and .claude/settings.json (the SessionStart hook) are the project's: " +
            "changed only after asking (or --yes)."

    private val yes by option("--yes", help = "Replace the project's templates and add the CLAUDE.md paragraph without asking").flag()
    private val dryRun by option("--dry-run", help = "Show what would change, write nothing").flag()

    override fun run() {
        val repo = repository
        val skill = repo.root / Install.SKILL_DIR / "SKILL.md"
        val before = when {
            !SystemFileSystem.exists(skill) -> "not installed"
            else -> Install.installedVersion(SystemFileSystem.read(skill) { readUtf8() }) ?: "installed by hand"
        }
        val changes = Install.plan(SystemFileSystem, repo.root, repo.config.dir)
        applyChanges(repo.root, changes, yes, dryRun, prompts)
        val managedChanged = changes.any { it.managed && it.action != FileAction.SAME }
        echo(
            if (managedChanged) "skill and spec: $before → ${Embedded.VERSION}${if (dryRun) " (dry run)" else ""}"
            else "skill and spec: already ${Embedded.VERSION}",
        )
    }
}
