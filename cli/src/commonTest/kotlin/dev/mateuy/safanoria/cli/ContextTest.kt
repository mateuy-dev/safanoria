package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.testing.test
import dev.mateuy.safanoria.core.Embedded
import dev.mateuy.safanoria.core.Install
import dev.mateuy.safanoria.core.SystemFileSystem
import okio.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextTest {
    private fun run(repo: GitRepo, claude: Path? = null) =
        cli(claudeDir = claude ?: (repo.root.parent!! / "no-claude")).test(listOf("--root", repo.root.toString(), "context"))

    @Test
    fun printsTheTicketOfATicketBranchOnly() {
        val repo = GitRepo.scenario("context")
        val onMain = run(repo)
        assertEquals(0, onMain.statusCode, onMain.output)
        assertEquals("", onMain.output, "not a ticket branch: nothing")

        repo.checkout("beta")
        val r = run(repo)
        assertEquals(0, r.statusCode, r.output)
        assertTrue("works on the Safanoria ticket `beta`" in r.stdout, r.stdout)
        assertTrue("<ticket file=\"" in r.stdout && "status: in-progress" in r.stdout, r.stdout)
        assertTrue("run `safanoria-cli finish beta` without being asked" in r.stdout, r.stdout)

        repo.checkout("feature")
        assertEquals("", run(repo).output, "a branch whose name has no ticket")
    }

    @Test
    fun warnsWhenTheSkillDoesNotMatchTheCli() {
        val repo = GitRepo.scenario("context-skill")
        repo.checkout("beta")
        val claude = repo.root.parent!! / "context-skill-claude"
        SystemFileSystem.deleteRecursively(claude)
        val skill = claude / Install.GLOBAL_SKILL_DIR
        assertTrue("The `safanoria` skill isn't installed" in run(repo, claude).stdout)

        repo.write(".claude/skills/safanoria/SKILL.md", "old\n\n<!-- safanoria 0.0.1 -->\n")
        val project = run(repo, claude).stdout
        assertTrue("skill in ${displayPath(repo.root / Install.SKILL_DIR)} is from Safanoria 0.0.1, but safanoria-cli is ${Embedded.VERSION}" in project, project)

        // The user's copy is the one Claude Code uses: current, so no warning, and the format is there.
        SystemFileSystem.createDirectories(skill)
        Install.managedFiles().filterKeys { it.startsWith("safanoria/") }
            .forEach { (file, text) -> SystemFileSystem.write(skill.parent!! / file) { writeUtf8(text) } }
        val current = run(repo, claude).stdout
        assertTrue("(format: ${displayPath(skill / "SPEC.md")}," in current, current)
        assertTrue("is from Safanoria" !in current && "isn't installed" !in current, current)
    }
}
