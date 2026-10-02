package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.testing.test
import dev.mateuy.safanoria.core.Frontmatter
import dev.mateuy.safanoria.core.Repository
import dev.mateuy.safanoria.core.SystemFileSystem
import dev.mateuy.safanoria.core.Validator
import dev.mateuy.safanoria.core.environmentVariable
import dev.mateuy.safanoria.core.text
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NewTest {
    private val repoRoot = (environmentVariable("SAFANORIA_REPO_ROOT") ?: error("run through Gradle")).toPath()
    private val fixture = repoRoot / "core" / "src" / "commonTest" / "fixtures" / "validate" / "valid"
    /** A fresh copy of the `valid` fixture: `new` writes files. */
    private val work: Path = repoRoot / "cli" / "build" / "test-repos" / "new"

    @BeforeTest
    fun copyFixture() {
        SystemFileSystem.deleteRecursively(work)
        SystemFileSystem.createDirectories(work / "tickets")
        SystemFileSystem.copy(fixture / "safanoria.yaml", work / "safanoria.yaml")
        SystemFileSystem.list(fixture / "tickets").forEach { SystemFileSystem.copy(it, work / "tickets" / it.name) }
    }

    private fun run(vararg args: String) = cli().test(listOf("--root", work.toString()) + args.toList())
    private fun read(path: Path) = SystemFileSystem.read(path) { readUtf8() }

    @Test
    fun createsATicket() {
        val r = run("new", "Herd photos from the field", "--size", "M", "--objective", "Users attach photos to a herd.", "--date", "2026-10-02")
        assertEquals(0, r.statusCode, r.output)
        assertTrue(r.stdout.contains("create ") && r.stdout.contains("herd-photos-field.md"), r.stdout)
        assertTrue(r.stdout.contains("suggested from the title"), r.stdout)
        val text = read(work / "tickets" / "herd-photos-field.md")
        val f = Frontmatter.parse(null, text).first!!
        assertEquals(listOf("herd-photos-field", "backlog", "M", "2026-10-02"), listOf(f.id!!.value, f.status!!.text, f.size!!.text, f.created!!.value))
        assertTrue(text.contains("Users attach photos to a herd."))
        assertEquals(emptyList(), Validator(Repository(work)).validate().map { it.toString() })
    }

    @Test
    fun veryLowPriority() {
        val r = run("new", "Dark app icon", "--priority", "very-low", "--date", "2026-10-02")
        assertEquals(0, r.statusCode, r.output)
        val f = Frontmatter.parse(null, read(work / "tickets" / "dark-app-icon.md")).first!!
        assertEquals("very-low", f.priority!!.text)
        assertEquals(emptyList(), Validator(Repository(work)).validate().map { it.toString() })
    }

    @Test
    fun childAndDryRun() {
        val before = read(work / "tickets" / "herd-locations.md")
        val dry = run("new", "Map pin", "--parent", "herd-locations", "--dry-run", "--date", "2026-10-02")
        assertEquals(0, dry.statusCode, dry.output)
        assertTrue(dry.stdout.contains("would create") && dry.stdout.contains("herd-locations-map-pin.md"), dry.stdout)
        assertFalse(SystemFileSystem.exists(work / "tickets" / "herd-locations-map-pin.md"))
        assertEquals(before, read(work / "tickets" / "herd-locations.md"))

        val r = run("new", "Map pin", "--parent", "herd-locations", "--id", "herd-locations-pin", "--date", "2026-10-02")
        assertEquals(0, r.statusCode, r.output)
        assertTrue(read(work / "tickets" / "herd-locations.md").contains("- [ ] `herd-locations-pin`: Map pin"))
        assertEquals(emptyList(), Validator(Repository(work)).validate().map { it.toString() })
    }

    @Test
    fun refusedWritesNothing() {
        val r = run("new", "Anything", "--id", "herd-locations")
        assertEquals(1, r.statusCode)
        assertTrue(r.stderr.contains("already exists"), r.stderr)
        val bad = run("new", "Anything", "--type", "epic")
        // A usage error. `test()` bypasses main(), which maps usage errors to 2; checked on the binary.
        assertTrue(bad.statusCode != 0 && bad.stderr.contains("invalid choice"), bad.output)
        assertEquals(6, SystemFileSystem.list(work / "tickets").size)
    }

    @Test
    fun warnsAboutAnExistingBranch() {
        // build/ is inside this repository's git work tree, so its branches are visible.
        val branch = dev.mateuy.safanoria.core.Git(repoRoot).currentBranch()
        if (branch == "HEAD" || !dev.mateuy.safanoria.core.isValidId(branch)) return // detached (CI pull requests)
        val r = run("new", "Same name as the branch", "--id", branch, "--dry-run")
        assertEquals(0, r.statusCode, r.output)
        assertTrue(r.stderr.contains("branch named '$branch' already exists"), r.stderr)
    }
}
