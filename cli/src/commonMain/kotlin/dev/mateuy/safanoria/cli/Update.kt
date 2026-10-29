package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import dev.mateuy.safanoria.core.Embedded
import dev.mateuy.safanoria.core.FileAction
import dev.mateuy.safanoria.core.FileChange
import dev.mateuy.safanoria.core.Install
import dev.mateuy.safanoria.core.SystemFileSystem

/**
 * `safanoria-cli update`: installs this version's skill and spec for the user (all projects) and,
 * when the project has its own copy, there too; offers its templates.
 */
class Update : RepositoryCommand(name = "update") {
    override fun help(context: Context) =
        "Install this version's skill and SPEC.md in ~/.claude/skills/safanoria/ and ~/.agents/skills/safanoria/ (all your projects and worktrees), replace " +
            "the project's own copy if it has one, and add missing templates. With --global, only the first, from anywhere. " +
            "Templates, the ticket README, CLAUDE.md, AGENTS.md and .claude/settings.json (the SessionStart hook) are the project's: " +
            "changed only after asking (or --yes)."

    private val global by option("--global", help = "Only the skill and SPEC.md in ~/.claude/skills/safanoria/ and ~/.agents/skills/safanoria/; no project needed").flag()
    private val yes by option("--yes", help = "Replace the project's templates and add the CLAUDE.md paragraph without asking").flag()
    private val dryRun by option("--dry-run", help = "Show what would change, write nothing").flag()

    override fun run() {
        val claudeDir = claudeDir
        val codexGlobal = agentsDir?.let { Install.codexGlobalPlan(SystemFileSystem, it) }.orEmpty()
        if (global) {
            claudeDir ?: throw PrintMessage("No home directory to install the skill in (HOME, CLAUDE_CONFIG_DIR).", 1, true)
            val skillDir = claudeDir / Install.GLOBAL_SKILL_DIR
            val before = Install.skillVersion(SystemFileSystem, skillDir)
            val changes = Install.globalPlan(SystemFileSystem, claudeDir)
            applyChanges(null, changes + codexGlobal, yes, dryRun, prompts)
            summary(skillDir.toString(), before, changes)
            return
        }
        val repo = repository
        val projectSkillDir = repo.root / Install.SKILL_DIR
        val projectBefore = Install.skillVersion(SystemFileSystem, projectSkillDir)
        val globalSkillDir = claudeDir?.let { it / Install.GLOBAL_SKILL_DIR }
        val globalBefore = globalSkillDir?.let { Install.skillVersion(SystemFileSystem, it) }
        val globalChanges = claudeDir?.let { Install.globalPlan(SystemFileSystem, it) }.orEmpty()
        val changes = Install.plan(SystemFileSystem, repo.root, repo.config.dir, projectSkill = projectBefore != null,
            codexProjectSkill = projectBefore != null || SystemFileSystem.exists(repo.root / Install.CODEX_SKILL_DIR / "SKILL.md"))
        applyChanges(repo.root, globalChanges + codexGlobal + changes, yes, dryRun, prompts)
        if (globalSkillDir != null) summary(globalSkillDir.toString(), globalBefore, globalChanges)
        else echo("no home directory to install the skill in (HOME, CLAUDE_CONFIG_DIR)", err = true)
        if (projectBefore != null) summary(Install.SKILL_DIR, projectBefore, changes.filter { it.managed })
    }

    /** One line: which version [skillDir] had and has now. */
    private fun summary(skillDir: String, before: String?, changes: List<FileChange>) {
        val changed = changes.any { it.managed && it.action != FileAction.SAME }
        echo(
            if (changed) "skill and spec in $skillDir: ${before ?: "not installed"} → ${Embedded.VERSION}${if (dryRun) " (dry run)" else ""}"
            else "skill and spec in $skillDir: already ${Embedded.VERSION}",
        )
    }
}
