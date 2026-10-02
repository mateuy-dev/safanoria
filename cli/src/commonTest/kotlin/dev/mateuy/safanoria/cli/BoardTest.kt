package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.testing.test
import dev.mateuy.safanoria.core.SystemFileSystem
import dev.mateuy.safanoria.core.environmentVariable
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BoardTest {
    private val repoRoot = (environmentVariable("SAFANORIA_REPO_ROOT") ?: error("run through Gradle")).toPath()
    private val fixture = repoRoot / "core" / "src" / "commonTest" / "fixtures" / "validate" / "valid"
    /** Checked by hand when written; regenerate with `safanoria --root <fixture> board` after a deliberate change. */
    private val golden = repoRoot / "cli" / "src" / "commonTest" / "fixtures" / "board" / "valid.md"

    /** CRLF-normalised: Windows checkouts may convert the golden file; the board is always LF. */
    private fun read(path: okio.Path) = SystemFileSystem.read(path) { readUtf8() }.replace("\r\n", "\n")

    @Test
    fun matchesGoldenFile() {
        val r = cli().test(listOf("--root", fixture.toString(), "board"))
        assertEquals(0, r.statusCode, r.output)
        assertEquals(read(golden), r.stdout)
    }

    @Test
    fun outputFileLinksRelativeToIt() {
        val dir = repoRoot / "cli" / "build" / "test-repos" / "board" / "docs"
        SystemFileSystem.deleteRecursively(dir)
        SystemFileSystem.createDirectories(dir)
        val r = cli().test(listOf("--root", fixture.toString(), "board", "--output", (dir / "BOARD.md").toString()))
        assertEquals(0, r.statusCode, r.output)
        val board = read(dir / "BOARD.md")
        val back = "../".repeat(5) // from cli/build/test-repos/board/docs up to repoRoot
        val link = back + fixture.relativeTo(repoRoot).segments.joinToString("/") + "/tickets/herd-locations.md"
        assertTrue(board.contains("[herd-locations]($link)"), board)
        assertEquals(read(golden).replace("](tickets/", "](" + link.removeSuffix("herd-locations.md")), board)
    }
}
