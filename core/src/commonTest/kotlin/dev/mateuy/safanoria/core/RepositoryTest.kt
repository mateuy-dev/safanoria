package dev.mateuy.safanoria.core

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepositoryTest {
    @Test
    fun onlyValidIdsAreTickets() {
        val fs = FakeFileSystem()
        val tickets = "/repo/issues".toPath()
        fs.createDirectories(tickets / "attachments" / "map-input")
        fs.write("/repo/safanoria.yaml".toPath()) { writeUtf8("safanoria: 1\nworktree: ../project--{id}\ndir: issues\ncomponents:\n  app:\n    external: true\n") }
        for (name in listOf("map-input.md", "README.md", "_TEMPLATE.md", "Bad.md", "a--b.md", "ab.md", "notes.txt")) {
            fs.write(tickets / name) { writeUtf8("---\nid: x\n---\n") }
        }
        val repo = Repository.find("/repo/issues/attachments".toPath(), fs)!!
        assertEquals("/repo".toPath(), repo.root)
        assertEquals(listOf("map-input.md"), repo.ticketPaths().map { it.name })
        assertNotNull(repo.ticket("map-input"))
        assertNull(repo.ticket("README"))
    }

    @Test
    fun ids() {
        assertTrue(isValidId("v1-tooling-cli-core"))
        assertFalse(isValidId("ab"))
        assertFalse(isValidId("a".repeat(41)))
        assertFalse(isValidId("map--input"))
        assertFalse(isValidId("map-"))
        assertFalse(isValidId("1map"))
    }

    @Test
    fun thisRepository() {
        val repo = Repository.find(repoRoot / "core")!!
        assertEquals(SystemFileSystem.canonicalize(repoRoot), repo.root)
        assertEquals(emptyList(), repo.configResult.diagnostics)
        assertEquals(ticketFiles(repoRoot).map { it.name }.sorted(), repo.tickets.map { it.path.name })
        assertEquals("v1-tooling", repo.ticket("v1-tooling-cli-core")!!.frontmatter!!.parent!!.value)
    }

    @Test
    fun git() {
        val git = Repository(repoRoot).git
        val branch = git.currentBranch()
        assertNotEquals("", branch)
        if (branch != "HEAD") assertTrue(git.branchExists(branch), "current branch $branch exists")
        assertFalse(git.branchExists("no-such-branch-for-safanoria-tests"))
        git.stagedFiles() // runs; content depends on the working tree
    }

    @Test
    fun failingCommandsReportExitCodeAndOutput() {
        val result = runCommand("git no-such-command 2>&1")
        assertNotEquals(0, result.exitCode)
        assertTrue(result.output.contains("no-such-command"), result.output)
    }
}
