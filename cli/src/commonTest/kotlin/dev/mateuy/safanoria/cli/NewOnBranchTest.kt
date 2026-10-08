package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.testing.test
import dev.mateuy.safanoria.core.SystemFileSystem
import okio.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NewOnBranchTest {
    private fun run(root: Path, vararg args: String) =
        cli().test(listOf("--root", root.toString(), "new", "--date", "2026-10-02") + args.toList())

    @Test
    fun onABranchNotCheckedOutCommitsWithoutTouchingTheCheckout() {
        val repo = GitRepo.scenario("new-on-plumbing")
        repo.checkout("feature")
        repo.write("tickets/alpha.md", GitRepo.ticket("alpha", "ready")) // the user's uncommitted work
        val head = repo.git("rev-parse", "HEAD")

        val r = run(repo.root, "Found while on feature", "--id", "found-work", "--on", "main")
        assertEquals(0, r.statusCode, r.output)
        assertEquals("create tickets/found-work.md on main", r.stdout.lines().first())
        assertTrue(r.stdout.contains(Regex("committed [0-9a-f]{7} on main: found-work: create")), r.stdout)

        assertEquals("found-work: create\n", repo.git("log", "-1", "--format=%s", "main"))
        assertTrue(repo.git("show", "main:tickets/found-work.md").contains("status: backlog"))
        assertEquals("tickets/alpha.md\ntickets/beta.md\ntickets/found-work.md\n", repo.git("ls-tree", "-r", "--name-only", "main", "tickets/"))
        // This checkout: same HEAD, no new file, the user's change still there and unstaged.
        assertEquals(head, repo.git("rev-parse", "HEAD"))
        assertFalse(SystemFileSystem.exists(repo.root / "tickets" / "found-work.md"))
        assertEquals(" M tickets/alpha.md\n", repo.git("status", "--porcelain"))
    }

    @Test
    fun onABranchCheckedOutElsewhereCommitsOnlyTheTicketThere() {
        val repo = GitRepo.scenario("new-on-worktree")
        val feature = repo.worktree("feature")
        repo.write("notes.txt", "staged, not part of the ticket\n")
        repo.git("add", "notes.txt")

        val r = run(feature, "Found while on feature", "--id", "found-work", "--on", "main")
        assertEquals(0, r.statusCode, r.output)
        assertTrue(SystemFileSystem.exists(repo.root / "tickets" / "found-work.md"))
        assertFalse(SystemFileSystem.exists(feature / "tickets" / "found-work.md"))
        assertEquals("tickets/found-work.md\n", repo.git("show", "--name-only", "--format=", "main"))
        assertEquals("A  notes.txt\n", repo.git("status", "--porcelain"), "the user's staged file stays staged")
    }

    @Test
    fun idsOnOtherBranchesAreTaken() {
        val repo = GitRepo.scenario("new-taken")
        val r = run(repo.root, "Stray again", "--id", "stray")
        assertEquals(1, r.statusCode, r.output)
        assertTrue("ticket 'stray' already exists on another branch" in r.stderr, r.stderr)
    }

    @Test
    fun hintsAtMainBranchFromAnotherBranch() {
        val repo = GitRepo.scenario("new-hint")
        repo.checkout("feature")
        val r = run(repo.root, "Something else", "--dry-run")
        assertEquals(0, r.statusCode, r.output)
        assertTrue("note: this is branch 'feature'; a top-level ticket belongs on main" in r.stderr, r.stderr)
        assertFalse("note:" in run(repo.root, "Something else", "--dry-run", "--on", "main").stderr)
    }

    @Test
    fun aProjectInASubdirectory() {
        val repo = GitRepo("new-on-subdir")
        repo.write("app/safanoria.yaml", GitRepo.config("app"))
        repo.write("app/tickets/alpha.md", GitRepo.ticket("alpha"))
        repo.write("README.md", "root\n")
        repo.commit("init")
        repo.checkout("feature", create = true)

        val r = run(repo.root / "app", "Nested", "--id", "nested", "--on", "main")
        assertEquals(0, r.statusCode, r.output)
        assertEquals("README.md\napp/safanoria.yaml\napp/tickets/alpha.md\napp/tickets/nested.md\n", repo.git("ls-tree", "-r", "--name-only", "main"))
    }

    @Test
    fun unknownBranch() {
        val repo = GitRepo.scenario("new-no-branch")
        val r = run(repo.root, "X", "--on", "nope")
        assertEquals(2, r.statusCode)
        assertTrue("No local branch 'nope'" in r.stderr, r.stderr)
    }
}
