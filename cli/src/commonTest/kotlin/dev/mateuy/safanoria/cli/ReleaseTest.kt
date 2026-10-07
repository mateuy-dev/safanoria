package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.testing.test
import dev.mateuy.safanoria.core.Repository
import dev.mateuy.safanoria.core.SystemFileSystem
import dev.mateuy.safanoria.core.Validator
import dev.mateuy.safanoria.core.environmentVariable
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReleaseTest {
    private val repoRoot = (environmentVariable("SAFANORIA_REPO_ROOT") ?: error("run through Gradle")).toPath()
    private val fixture = repoRoot / "core" / "src" / "commonTest" / "fixtures" / "validate" / "valid"
    /** A fresh copy of the `valid` fixture with a version file and a ticket to stamp. */
    private val work: Path = repoRoot / "cli" / "build" / "test-repos" / "release"
    private val photo get() = work / "tickets" / "photo-upload.md"

    @BeforeTest
    fun copyFixture() {
        SystemFileSystem.deleteRecursively(work)
        SystemFileSystem.createDirectories(work / "tickets")
        SystemFileSystem.copy(fixture / "safanoria.yaml", work / "safanoria.yaml")
        SystemFileSystem.list(fixture / "tickets").forEach { SystemFileSystem.copy(it, work / "tickets" / it.name) }
        SystemFileSystem.write(work / "gradle.properties") { writeUtf8("version=1.1.0\n") }
        SystemFileSystem.write(photo) {
            writeUtf8(
                "---\nid: photo-upload\ntype: feature\ntitle: Photo upload\nstatus: done\npriority: medium\nsize: S\n" +
                    "created: 2026-10-01\nupdated: 2026-10-01\n---\n\n## Objective\n\nWhy.\n\n## Acceptance Criteria\n\n" +
                    "- [x] Works\n\n## Plan\n\n- [x] Do it\n\n## Work Log\n\n- **2026-10-01** · status · Done.\n",
            )
        }
    }

    private fun run(vararg args: String) = cli().test(listOf("--root", work.toString(), "release") + args.toList() + listOf("--date", "2026-10-02"))
    private fun read(path: Path) = SystemFileSystem.read(path) { readUtf8() }

    @Test
    fun refusesOffTheMainBranch() {
        // The work copy is inside this repository: on a ticket branch (or a detached HEAD in CI)
        // it is off 'main'. On main itself there is nothing to check.
        if (dev.mateuy.safanoria.core.Git(repoRoot).currentBranch() == "main") return
        val r = run("app")
        assertEquals(1, r.statusCode, r.output)
        assertTrue(r.stderr.contains("not 'main'") && r.stderr.contains("--any-branch"), r.stderr)
        assertTrue(!read(photo).contains("resolvedIn"))

        val dry = run("app", "--dry-run")
        assertEquals(0, dry.statusCode, "a dry run previews from any branch")
        assertTrue(dry.stderr.startsWith("warning: on ") && dry.stdout.startsWith("would stamp app 1.1.0"), dry.output)
    }

    @Test
    fun aStampedTicketMovesFromToReleaseToReleasedOnTheBoard() {
        fun board() = cli().test(listOf("--root", work.toString(), "board", "--checkout")).stdout
        assertTrue(board().contains("## To release (1)\n\n- [photo-upload](tickets/photo-upload.md) Photo upload\n"), board())
        assertEquals(0, run("app", "--any-branch").statusCode)
        assertTrue(!board().contains("## To release"), board())
        assertTrue(board().contains("- [photo-upload](tickets/photo-upload.md) Photo upload · app 1.1.0\n"), board())
    }

    @Test
    fun stampsFromTheVersionSource() {
        val dry = run("app", "--any-branch", "--dry-run")
        assertEquals(0, dry.statusCode, dry.output)
        assertTrue(dry.stdout.startsWith("would stamp app 1.1.0 (from gradle.properties) on 1 ticket:\n"), dry.stdout)
        assertTrue(!read(photo).contains("resolvedIn"), "dry run writes nothing")

        val r = run("app", "--any-branch")
        assertEquals(0, r.statusCode, r.output)
        assertTrue(r.stdout.startsWith("stamped app 1.1.0 (from gradle.properties) on 1 ticket:\n") && r.stdout.contains("photo-upload.md"), r.stdout)
        val text = read(photo)
        assertTrue(text.contains("updated: 2026-10-02\nresolvedIn:\n  app: 1.1.0\n") && text.contains("- **2026-10-02** · release · app 1.1.0\n"), text)
        assertEquals(emptyList(), Validator(Repository(work)).validate().map { it.toString() })

        val again = run("app", "--any-branch")
        assertEquals(0, again.statusCode, again.output)
        assertTrue(again.stdout.startsWith("nothing to stamp with app 1.1.0"), again.stdout)
    }

    @Test
    fun givenVersionAndRefusals() {
        val r = run("app", "1.2.0", "--any-branch", "--ticket", "photo-upload")
        assertEquals(0, r.statusCode, r.output)
        assertTrue(r.stdout.startsWith("stamped app 1.2.0 on 1 ticket"), r.stdout)
        assertTrue(r.stderr.contains("warning: gradle.properties says 1.1.0, stamping 1.2.0 as given"), r.stderr)

        val lower = run("app", "0.9.0", "--any-branch")
        assertEquals(1, lower.statusCode)
        assertTrue(lower.stderr.contains("lower than 1.2.0"), lower.stderr)
        assertEquals(1, run("app", "--any-branch", "--ticket", "herd-locations-map").statusCode, "not done")
    }
}
