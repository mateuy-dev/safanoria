package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.testing.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FinishTest {
    private fun run(repo: GitRepo, vararg args: String) =
        cli().test(listOf("--root", repo.root.toString()) + args.toList() + listOf("--date", "2026-10-04"))

    @Test
    fun reviewIsCommittedInTheWorktree() {
        val repo = GitRepo.scenario("finish-worktree")
        val wt = repo.worktree("beta")
        val r = run(repo, "finish", "beta")
        assertEquals(0, r.statusCode, r.output)
        val text = repo.read("tickets/beta.md", at = wt)
        assertTrue("status: review\n" in text && "updated: 2026-10-04\n" in text, text)
        assertTrue(text.endsWith("- **2026-10-04** · status · review\n"), text)
        assertEquals("beta: review", repo.git("log", "-1", "--format=%s", "beta").trim())
        assertEquals("", repo.git("status", "--porcelain", at = wt).trim())
        assertTrue("status: backlog" in repo.read("tickets/beta.md"), "main's copy is untouched")
    }

    @Test
    fun reviewOnABranchNotCheckedOut() {
        val repo = GitRepo.scenario("finish-branch")
        val r = run(repo, "finish", "beta")
        assertEquals(0, r.statusCode, r.output)
        assertTrue("status: review" in repo.git("show", "beta:tickets/beta.md"))
        assertEquals("main", repo.git("branch", "--show-current").trim())
    }

    @Test
    fun reviewWithAReferenceToATicketCreatedOnMainMeanwhile() {
        val repo = GitRepo.scenario("finish-related")
        repo.write("tickets/late.md", GitRepo.ticket("late"))
        repo.commit("late on main") // as `new --on main` from beta's branch: beta doesn't have the file
        repo.checkout("beta")
        repo.write("tickets/beta.md", GitRepo.ticket("beta", "in-progress", related = listOf("late")))
        repo.commit("beta relates to late")

        val r = run(repo, "finish", "beta")
        assertEquals(0, r.statusCode, r.output)
        assertTrue("status: review" in repo.read("tickets/beta.md"))
    }

    @Test
    fun doneOnlyOnceMergedAndChecksTheParent() {
        val repo = GitRepo.scenario("finish-done")
        repo.write("tickets/gamma.md", GitRepo.ticket("gamma").replace("updated: 2026-10-02\n", "updated: 2026-10-02\nchildrenMergeInto: main\n")
            .replace("- [ ] Step", "- [ ] `delta`: Child"))
        repo.write("tickets/delta.md", GitRepo.ticket("delta").replace("updated: 2026-10-02\n", "updated: 2026-10-02\nparent: gamma\n"))
        repo.commit("gamma and delta")
        repo.checkout("delta", create = true)
        repo.write("tickets/delta.md", repo.read("tickets/delta.md").replace("status: backlog", "status: in-progress"))
        repo.commit("delta started")
        repo.checkout("main")

        val early = run(repo, "finish", "delta", "--done")
        assertEquals(1, early.statusCode, early.output)
        assertTrue("isn't merged" in early.stderr, early.stderr)

        repo.git("merge", "-q", "--no-ff", "--no-edit", "delta")
        val r = run(repo, "finish", "delta", "--done")
        assertEquals(0, r.statusCode, r.output)
        assertTrue("status: done" in repo.read("tickets/delta.md"))
        assertTrue("- [x] `delta`: Child" in repo.read("tickets/gamma.md"), repo.read("tickets/gamma.md"))
        assertEquals("delta: done", repo.git("log", "-1", "--format=%s").trim())
        assertEquals("", repo.git("status", "--porcelain").trim())
    }

    @Test
    fun refusesATicketNotStarted() {
        val repo = GitRepo.scenario("finish-refused")
        val r = run(repo, "finish", "alpha")
        assertEquals(1, r.statusCode)
        assertTrue("'alpha' is backlog" in r.stderr, r.stderr)
    }
}
