package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.testing.test
import dev.mateuy.safanoria.core.Embedded
import dev.mateuy.safanoria.core.Repository
import dev.mateuy.safanoria.core.SystemFileSystem
import dev.mateuy.safanoria.core.Validator
import dev.mateuy.safanoria.core.environmentVariable
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InitTest {
    private val repoRoot = (environmentVariable("SAFANORIA_REPO_ROOT") ?: error("run through Gradle")).toPath()
    private val work: Path = repoRoot / "cli" / "build" / "test-repos" / "init"

    @BeforeTest
    fun emptyProject() {
        SystemFileSystem.deleteRecursively(work)
        SystemFileSystem.createDirectories(work)
    }

    private fun run(vararg args: String, prompts: Prompts? = null) =
        cli(prompts).test(listOf("--root", work.toString(), "init") + args.toList())
    private fun read(path: String) = SystemFileSystem.read(work / path) { readUtf8() }
    private fun exists(path: String) = SystemFileSystem.exists(work / path)

    @Test
    fun withOptions() {
        val r = run("--component", "app=composeApp/gradle.properties:appVersionName", "--external", "rails", "--dir", "issues")
        assertEquals(0, r.statusCode, r.output)
        val config = read("safanoria.yaml")
        assertTrue(config.contains("dir: issues\n") && config.contains("    version: { file: composeApp/gradle.properties, property: appVersionName }\n") &&
            config.contains("  rails:\n    external: true\n"), config)
        for (f in listOf("issues/_TEMPLATE.md", "issues/_TEMPLATE.bug.md", "issues/_TEMPLATE.research.md", "issues/README.md", "CLAUDE.md")) {
            assertTrue(exists(f), f)
        }
        assertTrue(read(".claude/skills/safanoria/SPEC.md").endsWith("<!-- safanoria ${Embedded.VERSION} -->\n"))
        assertTrue(read("CLAUDE.md").contains("`issues/<id>.md`"))
        assertEquals(emptyList(), Validator(Repository(work)).validate().map { it.toString() })

        val again = run("--external", "rails")
        assertEquals(1, again.statusCode)
        assertTrue(again.stderr.contains("use `safanoria update`"), again.stderr)
    }

    @Test
    fun asksForComponents() {
        // Two names, then where each one's version is: a file (the default one), and external.
        val r = run(prompts = ScriptedPrompts("app, server", "file", "", "external"))
        assertEquals(0, r.statusCode, r.output)
        val config = read("safanoria.yaml")
        assertTrue(config.contains("  app:\n    version: { file: gradle.properties, property: version }\n  server:\n    external: true\n"), config)
    }

    @Test
    fun existingClaudeMd() {
        SystemFileSystem.write(work / "CLAUDE.md") { writeUtf8("# Project\n\nBuild with gradle.\n") }
        val kept = run("--external", "web")
        assertEquals(0, kept.statusCode, kept.output)
        assertTrue(kept.stdout.contains("kept CLAUDE.md: it doesn't point agents to the safanoria skill, and there is no terminal to ask"), kept.stdout)
        assertEquals("# Project\n\nBuild with gradle.\n", read("CLAUDE.md"))

        SystemFileSystem.delete(work / "safanoria.yaml")
        val asked = run("--external", "web", prompts = ScriptedPrompts(true))
        assertEquals(0, asked.statusCode, asked.output)
        assertTrue(read("CLAUDE.md").startsWith("# Project\n\nBuild with gradle.\n\nWork is tracked as Safanoria tickets"), read("CLAUDE.md"))
    }

    @Test
    fun refusals() {
        assertEquals(1, run().statusCode, "no components and no terminal") // usage error; main maps it to 2
        assertTrue(run().stderr.contains("no terminal to ask"))
        assertTrue(run("--component", "app=gradle.properties").stderr.contains("must be NAME=FILE:PROPERTY"))
        assertTrue(run("--external", "App").stderr.contains("not a component name"))
        assertTrue(run("--external", "a", "--external", "a").stderr.contains("given twice"))
        assertTrue(run("--external", "a", "--dir", "../x").stderr.contains("inside the project"))
        assertFalse(exists("safanoria.yaml"))

        val dry = run("--external", "web", "--dry-run")
        assertEquals(0, dry.statusCode, dry.output)
        assertTrue(dry.stdout.contains("would create safanoria.yaml") && dry.stdout.contains("would create CLAUDE.md"), dry.stdout)
        assertFalse(exists("safanoria.yaml"))
    }
}
