package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.testing.test
import dev.mateuy.safanoria.core.SystemFileSystem
import dev.mateuy.safanoria.core.environmentVariable
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotesTest {
    private val repoRoot = (environmentVariable("SAFANORIA_REPO_ROOT") ?: error("run through Gradle")).toPath()
    private val work: Path = repoRoot / "cli" / "build" / "test-repos" / "notes"

    private fun ticket(id: String, type: String, version: String, front: String = "") =
        "---\nid: $id\ntype: $type\ntitle: Title of $id\nstatus: done\npriority: medium\nsize: S\n" +
            "created: 2026-10-01\nupdated: 2026-10-01\n${front}resolvedIn:\n  app: $version\n---\n\n## Objective\n\nWhy $id.\n\n" +
            "## Acceptance Criteria\n\n- [x] Works\n\n## Plan\n\n- [x] Do it\n\n## Work Log\n\n- **2026-10-01** · status · Done.\n"

    @BeforeTest
    fun writeRepository() {
        SystemFileSystem.deleteRecursively(work)
        SystemFileSystem.createDirectories(work / "tickets")
        SystemFileSystem.write(work / "safanoria.yaml") { writeUtf8(GitRepo.config("notes")) }
        mapOf(
            "herd-map" to ticket("herd-map", "feature", "1.1.0"),
            "herd-map-tiles" to ticket("herd-map-tiles", "feature", "1.1.0", front = "parent: herd-map\n"),
            "save-crash" to ticket("save-crash", "bug", "1.2.0"),
        ).forEach { (id, text) -> SystemFileSystem.write(work / "tickets" / "$id.md") { writeUtf8(text) } }
    }

    private fun run(vararg args: String) = cli().test(listOf("--root", work.toString(), "notes") + args.toList())

    @Test
    fun printsTheTicketsOfAVersion() {
        val r = run("app", "1.1.0")
        assertEquals(0, r.statusCode, r.output)
        assertEquals(
            "# app 1.1.0\n\n## `herd-map` · feature · Title of herd-map\n\nWhy herd-map.\n\n" +
                "## `herd-map-tiles` · feature · Title of herd-map-tiles\nparent: `herd-map`\n\nWhy herd-map-tiles.\n",
            r.stdout,
        )
    }

    @Test
    fun betweenVersionsNewestFirst() {
        val r = run("app", "1.0.0", "1.2.0")
        assertEquals(0, r.statusCode, r.output)
        assertTrue(r.stdout.startsWith("# app 1.2.0\n\n## `save-crash` · bug · Title of save-crash\n\nWhy save-crash.\n\n# app 1.1.0\n"), r.stdout)
        assertTrue(!run("app", "1.1.0", "1.2.0").stdout.contains("herd-map"), "the first version is excluded")
    }

    @Test
    fun nothingStampedAndRefusals() {
        val none = run("app", "3.0.0")
        assertEquals(0, none.statusCode, none.output)
        assertEquals("", none.stdout)
        assertTrue(none.stderr.contains("no ticket has resolvedIn.app 3.0.0 (stamped versions: 1.1.0, 1.2.0)"), none.stderr)
        assertEquals(1, run("web", "1.0.0").statusCode)
        assertEquals(1, run("app", "1.2.0", "1.1.0").statusCode)
        assertTrue(run("app").stderr.contains("missing argument"), "the version is required")
    }
}
