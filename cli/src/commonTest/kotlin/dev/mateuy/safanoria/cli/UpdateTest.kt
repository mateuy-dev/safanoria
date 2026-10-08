package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.testing.test
import dev.mateuy.safanoria.core.Embedded
import dev.mateuy.safanoria.core.SystemFileSystem
import dev.mateuy.safanoria.core.environmentVariable
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateTest {
    private val repoRoot = (environmentVariable("SAFANORIA_REPO_ROOT") ?: error("run through Gradle")).toPath()
    private val work: Path = repoRoot / "cli" / "build" / "test-repos" / "update"
    private val ownTemplate = "---\nid: the-ticket-id\n---\n\n## Objective\n\nOur own.\n"

    /** Set up by hand, like VacAppKMP before: unmarked skill, own template and README, CLAUDE.md with the paragraph. */
    @BeforeTest
    fun handInstalledProject() {
        SystemFileSystem.deleteRecursively(work)
        SystemFileSystem.createDirectories(work / ".claude" / "skills" / "safanoria")
        SystemFileSystem.createDirectories(work / "tickets")
        write("safanoria.yaml", "safanoria: 1\nworktree: ../project--{id}\ncomponents:\n  app:\n    external: true\n")
        write(".claude/skills/safanoria/SKILL.md", "old skill\n")
        write(".claude/skills/safanoria/SPEC.md", "old spec\n")
        write("tickets/_TEMPLATE.md", ownTemplate)
        write("tickets/README.md", "# Our tickets\n")
        write("CLAUDE.md", "When working on a ticket, use the `safanoria` skill.\n")
    }

    private fun write(path: String, text: String) = SystemFileSystem.write(work / path) { writeUtf8(text) }
    private fun read(path: String) = SystemFileSystem.read(work / path) { readUtf8() }
    private fun run(vararg args: String, prompts: Prompts? = null) =
        cli(prompts).test(listOf("--root", work.toString(), "update") + args.toList())

    @Test
    fun replacesSkillKeepsTheProjectsFiles() {
        val r = run()
        assertEquals(0, r.statusCode, r.output)
        val out = r.stdout
        assertTrue(out.contains("replace .claude/skills/safanoria/SKILL.md\nreplace .claude/skills/safanoria/SPEC.md\n"), out)
        assertTrue(out.contains("kept tickets/_TEMPLATE.md: it differs from Safanoria's, and there is no terminal to ask"), out)
        assertTrue(out.contains("create tickets/_TEMPLATE.bug.md\ncreate tickets/_TEMPLATE.research.md\nunchanged tickets/README.md\nunchanged CLAUDE.md\n"), out)
        assertTrue(out.endsWith("skill and spec: installed by hand → ${Embedded.VERSION}\n"), out)
        assertTrue(read(".claude/skills/safanoria/SKILL.md").endsWith("<!-- safanoria ${Embedded.VERSION} -->\n"))
        assertEquals(ownTemplate, read("tickets/_TEMPLATE.md"))

        val again = run()
        assertTrue(again.stdout.endsWith("skill and spec: already ${Embedded.VERSION}\n"), again.stdout)
    }

    @Test
    fun fromAnOlderVersion() {
        write(".claude/skills/safanoria/SKILL.md", "old skill\n\n<!-- safanoria 0.0.9 -->\n")
        assertTrue(run().stdout.endsWith("skill and spec: 0.0.9 → ${Embedded.VERSION}\n"))
    }

    @Test
    fun templatesOnlyWhenAgreed() {
        val no = run(prompts = ScriptedPrompts(false))
        assertTrue(no.stdout.contains("kept tickets/_TEMPLATE.md: it differs"), no.stdout)
        assertEquals(ownTemplate, read("tickets/_TEMPLATE.md"))

        val yes = run(prompts = ScriptedPrompts(true))
        assertTrue(yes.stdout.contains("replace tickets/_TEMPLATE.md"), yes.stdout)
        assertEquals(Embedded.TICKET_TEMPLATE, read("tickets/_TEMPLATE.md"))
    }

    @Test
    fun dryRunWritesNothing() {
        val r = run("--dry-run", "--yes")
        assertEquals(0, r.statusCode, r.output)
        assertTrue(r.stdout.contains("would replace .claude/skills/safanoria/SKILL.md") && r.stdout.contains("would replace tickets/_TEMPLATE.md"), r.stdout)
        assertEquals("old skill\n", read(".claude/skills/safanoria/SKILL.md"))
        assertEquals(ownTemplate, read("tickets/_TEMPLATE.md"))
    }
}
