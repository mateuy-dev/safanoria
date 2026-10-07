package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.testing.test
import dev.mateuy.safanoria.core.SystemFileSystem
import okio.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Review to done: `finish`'s checks, `merge`, `reopen`, and the sweep of merges made elsewhere. */
class MergeTest {
    private fun run(repo: GitRepo, vararg args: String) =
        cli().test(listOf("--root", repo.root.toString()) + args.toList() + listOf("--date", "2026-10-05"))

    /** The scenario with beta's worktree, some work committed there, and beta in review. */
    private fun inReview(name: String): Pair<GitRepo, Path> {
        val repo = GitRepo.scenario(name)
        val wt = repo.worktree("beta")
        repo.write("code.txt", "beta\n", at = wt)
        repo.commit("beta work", at = wt)
        val r = run(repo, "finish", "beta")
        assertEquals(0, r.statusCode, r.output)
        return repo to wt
    }

    private fun GitRepo.head(branch: String = "main") = git("rev-parse", branch).trim()

    @Test
    fun mergeLandsTheTicketInOneMergeCommit() {
        val (repo, wt) = inReview("merge-lands")
        val r = run(repo, "merge", "beta")
        assertEquals(0, r.statusCode, r.output)

        assertEquals("beta: merge (done)", repo.git("log", "-1", "--format=%s").trim())
        assertEquals(3, repo.git("rev-list", "--parents", "-1", "main").trim().split(' ').size, "a merge commit")
        val text = repo.read("tickets/beta.md")
        assertTrue("status: done\n" in text && text.endsWith("- **2026-10-05** · status · done\n"), text)
        assertEquals("beta\n", repo.read("code.txt"))
        assertEquals("", repo.git("status", "--porcelain").trim())
        assertFalse(SystemFileSystem.exists(wt), "the worktree is removed")
        assertEquals("", repo.git("branch", "--list", "beta").trim())
        assertTrue("nothing was pushed" in r.stdout, r.stdout)
    }

    @Test
    fun mergeRunsFromTheTicketsOwnWorktree() {
        val (repo, wt) = inReview("merge-inside")
        val r = cli().test(listOf("--root", wt.toString(), "merge", "beta", "--date", "2026-10-05"))
        assertEquals(0, r.statusCode, r.output)
        assertTrue("status: done" in repo.read("tickets/beta.md"))
        assertFalse(SystemFileSystem.exists(wt))
        assertTrue("this directory was its worktree and is gone" in r.stdout, r.stdout)
    }

    /** A change the review asks for is committed while the ticket stays in review (SPEC §6.1): merge takes it. */
    @Test
    fun mergeTakesCommitsMadeAfterReview() {
        val (repo, wt) = inReview("merge-review-fix")
        repo.write("code.txt", "beta, fixed\n", at = wt)
        repo.commit("review fix", at = wt)
        assertTrue("status: review\n" in repo.read("tickets/beta.md", at = wt))

        val r = run(repo, "merge", "beta")
        assertEquals(0, r.statusCode, r.output)
        assertEquals("beta, fixed\n", repo.read("code.txt"))
        assertTrue("status: done\n" in repo.read("tickets/beta.md"))
    }

    @Test
    fun withoutAWorktreeTheCheckoutGoesBackToTheTarget() {
        val repo = GitRepo.scenario("merge-switch")
        repo.checkout("beta")
        repo.write("code.txt", "beta\n")
        repo.commit("beta work")
        assertEquals(0, run(repo, "finish", "beta").statusCode)

        val r = run(repo, "merge", "beta")
        assertEquals(0, r.statusCode, r.output)
        assertEquals("main", repo.git("branch", "--show-current").trim())
        assertTrue("status: done" in repo.read("tickets/beta.md") && repo.read("code.txt") == "beta\n")
        assertEquals("", repo.git("branch", "--list", "beta").trim())
        assertEquals("", repo.git("status", "--porcelain").trim())
    }

    @Test
    fun mergeRefusesAndChangesNothing() {
        val (repo, wt) = inReview("merge-refuses")
        val main = repo.head()

        repo.write("scratch.txt", "x\n", at = wt)
        val dirty = run(repo, "merge", "beta")
        assertEquals(1, dirty.statusCode, dirty.output)
        assertTrue("uncommitted changes (1 file)" in dirty.stderr, dirty.stderr)
        SystemFileSystem.delete(wt / "scratch.txt")

        repo.write("code.txt", "main\n")
        repo.commit("main work")
        val conflict = run(repo, "merge", "beta")
        assertEquals(1, conflict.statusCode, conflict.output)
        assertTrue("conflicts in code.txt" in conflict.stderr, conflict.stderr)
        assertEquals("main work", repo.git("log", "-1", "--format=%s").trim())
        assertTrue(SystemFileSystem.exists(wt) && "status: review" in repo.read("tickets/beta.md", at = wt))

        val early = run(repo, "merge", "alpha")
        assertTrue("only a ticket in review is merged" in early.stderr, early.stderr)
        assertTrue(main != repo.head() && repo.head("beta") == repo.git("rev-parse", "HEAD", at = wt).trim())
    }

    @Test
    fun mergeChecksTheParentsPlanInTheSameCommit() {
        val repo = GitRepo.scenario("merge-parent")
        repo.write("tickets/gamma.md", GitRepo.ticket("gamma").replace("updated: 2026-10-02\n", "updated: 2026-10-02\nchildrenMergeInto: main\n")
            .replace("- [ ] Step", "- [ ] `delta`: Child"))
        repo.write("tickets/delta.md", GitRepo.ticket("delta").replace("updated: 2026-10-02\n", "updated: 2026-10-02\nparent: gamma\n"))
        repo.commit("gamma and delta")
        repo.checkout("delta", create = true)
        repo.write("tickets/delta.md", repo.read("tickets/delta.md").replace("status: backlog", "status: review"))
        repo.commit("delta in review")
        repo.checkout("main")

        val r = run(repo, "merge", "delta")
        assertEquals(0, r.statusCode, r.output)
        assertTrue("- [x] `delta`: Child" in repo.read("tickets/gamma.md"), repo.read("tickets/gamma.md"))
        assertTrue("status: done" in repo.read("tickets/delta.md"))
        assertEquals("delta: merge (done)", repo.git("log", "-1", "--format=%s").trim())
        assertEquals("", repo.git("status", "--porcelain").trim())
        assertEquals("", repo.git("branch", "--list", "delta").trim())
    }

    @Test
    fun finishChecksTheBranchBeforeReview() {
        val repo = GitRepo.scenario("finish-checks")
        val wt = repo.worktree("beta")
        repo.write("code.txt", "beta\n", at = wt)

        val dirty = run(repo, "finish", "beta")
        assertEquals(1, dirty.statusCode, dirty.output)
        assertTrue("uncommitted changes (1 file)" in dirty.stderr, dirty.stderr)
        repo.commit("beta work", at = wt)

        // A ticket created on main (SPEC §14.2) doesn't leave the branch behind; code does.
        repo.write("tickets/late.md", GitRepo.ticket("late"))
        repo.commit("late on main")
        assertEquals(0, run(repo, "finish", "beta", "--dry-run").statusCode)
        repo.write("other.txt", "main\n")
        repo.commit("main work")
        val behind = run(repo, "finish", "beta")
        assertEquals(1, behind.statusCode, behind.output)
        assertTrue("is behind main by 1 commit" in behind.stderr, behind.stderr)
        assertTrue("status: in-progress" in repo.read("tickets/beta.md", at = wt))

        repo.git("merge", "-q", "--no-edit", "main", at = wt)
        val r = run(repo, "finish", "beta")
        assertEquals(0, r.statusCode, r.output)
        assertTrue("  Acceptance Criteria, unchecked: Done" in r.stdout, r.stdout)
        assertTrue("code.txt" in r.stdout && "next: safanoria-cli merge beta" in r.stdout, r.stdout)
    }

    @Test
    fun reopenGoesBackToInProgressWithTheReason() {
        val (repo, wt) = inReview("reopen")
        val missing = run(repo, "reopen", "beta")
        assertTrue(missing.statusCode != 0 && "missing option --reason" in missing.stderr, missing.output)

        val r = run(repo, "reopen", "beta", "--reason", "The list isn't sorted")
        assertEquals(0, r.statusCode, r.output)
        val text = repo.read("tickets/beta.md", at = wt)
        assertTrue("status: in-progress\n" in text, text)
        assertTrue(text.endsWith("- **2026-10-05** · status · reopened: The list isn't sorted\n"), text)
        assertEquals("beta: reopen", repo.git("log", "-1", "--format=%s", "beta").trim())
        assertEquals("", repo.git("status", "--porcelain", at = wt).trim())

        assertEquals(1, run(repo, "reopen", "beta", "--reason", "again").statusCode)
    }

    @Test
    fun doneWithoutAnIdSweepsWhatWasMergedElsewhere() {
        val (repo, wt) = inReview("sweep")
        assertEquals("nothing to set done: no ticket in review is merged into its target", run(repo, "finish", "--done").stdout.trim())
        repo.git("merge", "-q", "--no-ff", "--no-edit", "beta")

        val warned = cli().test(listOf("--root", repo.root.toString(), "validate"))
        assertEquals(0, warned.statusCode, warned.output)
        assertTrue("warning[review-merged]: 'beta' is merged into main but still review" in warned.stdout, warned.output)

        val r = run(repo, "finish", "--done")
        assertEquals(0, r.statusCode, r.output)
        assertTrue("status: done" in repo.read("tickets/beta.md"))
        assertEquals("beta: done", repo.git("log", "-1", "--format=%s").trim())
        assertFalse(SystemFileSystem.exists(wt))
        assertEquals("", repo.git("branch", "--list", "beta").trim())
        assertTrue("review-merged" !in cli().test(listOf("--root", repo.root.toString(), "validate")).output)
    }

    @Test
    fun theSweepKeepsAWorktreeWithUncommittedFiles() {
        val (repo, wt) = inReview("sweep-dirty")
        repo.git("merge", "-q", "--no-ff", "--no-edit", "beta")
        repo.write("notes.txt", "mine\n", at = wt)

        val r = run(repo, "finish", "--done")
        assertEquals(0, r.statusCode, r.output)
        assertTrue("status: done" in repo.read("tickets/beta.md"))
        assertTrue("it has uncommitted changes" in r.stdout, r.stdout)
        assertEquals("mine\n", repo.read("notes.txt", at = wt))
        assertTrue(repo.git("branch", "--list", "beta").isNotBlank())
    }

    @Test
    fun releaseSetsMergedTicketsDoneBeforeStamping() {
        val (repo, wt) = inReview("sweep-release")
        repo.git("merge", "-q", "--no-ff", "--no-edit", "beta")

        val r = run(repo, "release", "app", "1.2.0")
        assertEquals(0, r.statusCode, r.output)
        assertTrue("set done, merged but still in review: beta" in r.stdout, r.stdout)
        val text = repo.read("tickets/beta.md")
        assertTrue("status: done\n" in text && "resolvedIn:\n  app: 1.2.0\n" in text, text)
        assertTrue(text.endsWith("- **2026-10-05** · status · done\n- **2026-10-05** · release · app 1.2.0\n"), text)
        assertFalse(SystemFileSystem.exists(wt))
    }
}
