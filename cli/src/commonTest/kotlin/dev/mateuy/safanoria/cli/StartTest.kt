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

    /** Where `start` puts the worktree of [id]: next to the repository ([GitRepo.config]). */
    private fun worktreeOf(repo: GitRepo, id: String) = repo.root.parent!! / "${repo.root.name}--$id"

    @Test
    fun branchesCommitsAndAddsTheWorktreeLeavingThisCheckout() {
        val repo = GitRepo.scenario("start-worktree")
        val wt = worktreeOf(repo, "alpha")
        val r = run(repo, "start", "alpha")
        assertEquals(0, r.statusCode, r.output)
        assertTrue("add worktree $wt" in r.stdout, r.stdout)
        assertEquals("main", repo.git("branch", "--show-current").trim())
        assertTrue("status: backlog" in repo.read("tickets/alpha.md"))
        assertEquals("alpha", repo.git("branch", "--show-current", at = wt).trim())
        val text = repo.read("tickets/alpha.md", at = wt)
        assertTrue("status: in-progress\n" in text, text)
        assertTrue("updated: 2026-10-03\n" in text, text)
        assertTrue(text.endsWith("- **2026-10-03** · status · started\n"), text)
        assertEquals("alpha: start", repo.git("log", "-1", "--format=%s", at = wt).trim())
        assertEquals("", repo.git("status", "--porcelain", at = wt).trim())
    }

    @Test
    fun startsATicketThatReferencesOneOnlyOnAnotherBranch() {
        val repo = GitRepo.scenario("start-related")
        repo.write("tickets/alpha.md", GitRepo.ticket("alpha", related = listOf("stray"))) // stray: only on feature
        repo.commit("alpha relates to stray")
        val r = run(repo, "start", "alpha")
        assertEquals(0, r.statusCode, r.output)
        assertTrue("status: in-progress\n" in repo.read("tickets/alpha.md", at = worktreeOf(repo, "alpha")))
    }

    @Test
    fun printPathPrintsOnlyTheWorktree() {
        val repo = GitRepo.scenario("start-print-worktree")
        val wt = worktreeOf(repo, "alpha")
        val dry = run(repo, "start", "alpha", "--print-path", "--dry-run")
        assertEquals(0, dry.statusCode, dry.output)
        assertEquals("", dry.stdout)

        val r = run(repo, "start", "alpha", "--print-path")
        assertEquals(0, r.statusCode, r.output)
        assertEquals("$wt\n", r.stdout)
        assertTrue("add worktree" in r.stderr, r.stderr)
        assertEquals("alpha", repo.git("branch", "--show-current", at = wt).trim())
    }

    @Test
    fun uncommittedChangesHereDontMatter() {
        val repo = GitRepo.scenario("start-dirty")
        repo.write("tickets/beta.md", GitRepo.ticket("beta") + "\nedited\n")
        val r = run(repo, "start", "alpha")
        assertEquals(0, r.statusCode, r.output)
        assertEquals("main", repo.git("branch", "--show-current").trim())
        assertTrue("edited" in repo.read("tickets/beta.md"))
        assertTrue("status: in-progress" in repo.read("tickets/alpha.md", at = worktreeOf(repo, "alpha")))
    }

    @Test
    fun withoutTheWorktreeSettingNothingStarts() {
        val repo = GitRepo.scenario("start-no-worktree")
        repo.write("safanoria.yaml", GitRepo.CONFIG)
        repo.commit("no worktree setting")
        val r = run(repo, "start", "alpha")
        assertEquals(1, r.statusCode, r.output)
        assertTrue("`worktree` is required" in r.stderr && "worktree: ../<project>--{id}" in r.stderr, r.stderr)
        assertFalse("alpha" in branches(repo))
    }

    @Test
    fun aChildStartsFromItsParentsBranch() {
        val repo = GitRepo.scenario("start-child")
        repo.checkout("beta")
        repo.write("tickets/beta.md", GitRepo.ticket("beta", "in-progress").replace("- [ ] Step", "- [ ] `beta-child`: Child"))
        repo.write("tickets/beta-child.md", GitRepo.ticket("beta-child").replace("updated: 2026-10-02\n", "updated: 2026-10-02\nparent: beta\n"))
        repo.commit("beta-child")
        repo.checkout("main")
        val r = run(repo, "start", "beta-child")
        assertEquals(0, r.statusCode, r.output)
        assertTrue("create branch beta-child from beta" in r.stdout, r.stdout)
        assertEquals("main", repo.git("branch", "--show-current").trim())
        assertEquals(repo.git("rev-parse", "beta").trim(), repo.git("rev-parse", "beta-child^").trim())
        assertEquals("beta-child", repo.git("branch", "--show-current", at = worktreeOf(repo, "beta-child")).trim())
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
