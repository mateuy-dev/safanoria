package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.testing.test
import dev.mateuy.safanoria.core.SystemFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartTest {
    private fun run(repo: GitRepo, vararg args: String) =
        cli().test(listOf("--root", repo.root.toString()) + args.toList() + listOf("--date", "2026-10-03"))

    private fun branches(repo: GitRepo) = repo.git("branch", "--format=%(refname:short)").lines().filter { it.isNotBlank() }

    @Test
    fun branchesCommitsAndSwitches() {
        val repo = GitRepo.scenario("start-switch")
        val r = run(repo, "start", "alpha")
        assertEquals(0, r.statusCode, r.output)
        assertTrue("switched to alpha" in r.stdout, r.stdout)
        assertEquals("alpha", repo.git("branch", "--show-current").trim())
        val text = repo.read("tickets/alpha.md")
        assertTrue("status: in-progress\n" in text, text)
        assertTrue("updated: 2026-10-03\n" in text, text)
        assertTrue(text.endsWith("- **2026-10-03** · status · started\n"), text)
        assertEquals("alpha: start", repo.git("log", "-1", "--format=%s").trim())
        assertEquals("", repo.git("status", "--porcelain").trim())
    }

    @Test
    fun startsATicketThatReferencesOneOnlyOnAnotherBranch() {
        val repo = GitRepo.scenario("start-related")
        repo.write("tickets/alpha.md", GitRepo.ticket("alpha", related = listOf("stray"))) // stray: only on feature
        repo.commit("alpha relates to stray")
        val r = run(repo, "start", "alpha")
        assertEquals(0, r.statusCode, r.output)
        assertTrue("status: in-progress\n" in repo.read("tickets/alpha.md"))
    }

    @Test
    fun withAWorktreeSettingAddsTheWorktreeAndLeavesThisCheckout() {
        val repo = GitRepo.scenario("start-worktree")
        repo.write("safanoria.yaml", GitRepo.CONFIG + "worktree: ../start-worktree--{id}\n")
        repo.commit("worktree setting")
        val wt = repo.root.parent!! / "start-worktree--alpha"
        SystemFileSystem.deleteRecursively(wt)
        val r = run(repo, "start", "alpha")
        assertEquals(0, r.statusCode, r.output)
        assertEquals("main", repo.git("branch", "--show-current").trim())
        assertTrue("status: backlog" in repo.read("tickets/alpha.md"))
        assertTrue("status: in-progress" in repo.read("tickets/alpha.md", at = wt))
        assertEquals("alpha", repo.git("branch", "--show-current", at = wt).trim())
    }

    @Test
    fun printPathPrintsOnlyTheWorktree() {
        val repo = GitRepo.scenario("start-print-worktree")
        repo.write("safanoria.yaml", GitRepo.CONFIG + "worktree: ../start-print-worktree--{id}\n")
        repo.commit("worktree setting")
        val wt = repo.root.parent!! / "start-print-worktree--alpha"
        SystemFileSystem.deleteRecursively(wt)
        val r = run(repo, "start", "alpha", "--print-path")
        assertEquals(0, r.statusCode, r.output)
        assertEquals("$wt\n", r.stdout)
        assertTrue("add worktree" in r.stderr, r.stderr)
        assertEquals("alpha", repo.git("branch", "--show-current", at = wt).trim())
    }

    @Test
    fun printPathWithoutAWorktreePrintsThisCheckout() {
        val repo = GitRepo.scenario("start-print-switch")
        val dry = run(repo, "start", "alpha", "--print-path", "--dry-run")
        assertEquals(0, dry.statusCode, dry.output)
        assertEquals("", dry.stdout)
        assertEquals(2, run(repo, "start", "alpha", "--print-path", "--no-switch").statusCode)
        assertFalse("alpha" in branches(repo))

        val r = run(repo, "start", "alpha", "--print-path")
        assertEquals(0, r.statusCode, r.output)
        assertEquals("${repo.root}\n", r.stdout)
        assertTrue("switched to alpha" in r.stderr, r.stderr)
    }

    @Test
    fun uncommittedChangesKeepTheCheckoutWhereItIs() {
        val repo = GitRepo.scenario("start-dirty")
        repo.write("tickets/beta.md", GitRepo.ticket("beta") + "\nedited\n")
        val r = run(repo, "start", "alpha")
        assertEquals(0, r.statusCode, r.output)
        assertTrue("git switch alpha" in r.stderr, r.stderr)
        assertEquals("main", repo.git("branch", "--show-current").trim())
        assertTrue("status: in-progress" in repo.git("show", "alpha:tickets/alpha.md"))
    }

    @Test
    fun aChildStartsFromItsParentsBranch() {
        val repo = GitRepo.scenario("start-child")
        repo.checkout("beta")
        repo.write("tickets/beta.md", GitRepo.ticket("beta", "in-progress").replace("- [ ] Step", "- [ ] `beta-child`: Child"))
        repo.write("tickets/beta-child.md", GitRepo.ticket("beta-child").replace("updated: 2026-10-02\n", "updated: 2026-10-02\nparent: beta\n"))
        repo.commit("beta-child")
        repo.checkout("main")
        val r = run(repo, "start", "beta-child", "--no-switch")
        assertEquals(0, r.statusCode, r.output)
        assertTrue("create branch beta-child from beta" in r.stdout, r.stdout)
        assertEquals("main", repo.git("branch", "--show-current").trim())
        assertEquals(repo.git("rev-parse", "beta").trim(), repo.git("rev-parse", "beta-child^").trim())
    }

    @Test
    fun refusals() {
        val repo = GitRepo.scenario("start-refused")
        assertTrue("no ticket 'nope'" in run(repo, "start", "nope").stderr)
        assertTrue("branch 'beta' already exists" in run(repo, "start", "beta").stderr)

        repo.write("tickets/done1.md", GitRepo.ticket("done1", "done"))
        repo.commit("done1")
        val done = run(repo, "start", "done1")
        assertEquals(1, done.statusCode)
        assertTrue("only backlog and ready" in done.stderr, done.stderr)
        assertFalse("done1" in branches(repo))
    }

    @Test
    fun dryRunChangesNothing() {
        val repo = GitRepo.scenario("start-dry")
        val r = run(repo, "start", "alpha", "--dry-run")
        assertEquals(0, r.statusCode, r.output)
        assertTrue("would create branch alpha from main" in r.stdout, r.stdout)
        assertFalse("alpha" in branches(repo))
    }
}
