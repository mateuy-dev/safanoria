package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.testing.test
import dev.mateuy.safanoria.core.Branches
import dev.mateuy.safanoria.core.DiffResult
import dev.mateuy.safanoria.core.Repository
import dev.mateuy.safanoria.core.TicketDiff
import okio.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * `diff <id>`: the ticket's changes from its merge-base with the target, touching nothing. The
 * command gives git the terminal, so what it shows is checked by running [TicketDiff.command] here.
 */
class DiffTest {
    private fun run(repo: GitRepo, vararg args: String, at: Path = repo.root) =
        cli().test(listOf("--root", at.toString()) + args.toList())

    /** beta's worktree with work committed there, and main moved on since beta started. */
    private fun withWork(name: String): Pair<GitRepo, Path> {
        val repo = GitRepo.scenario(name)
        val wt = repo.worktree("beta")
        repo.write("code.txt", "one\n", at = wt)
        repo.commit("beta work", at = wt)
        repo.write("main.txt", "main only\n")
        repo.commit("main moves on")
        return repo to wt
    }

    private fun GitRepo.prepare(id: String, uncommitted: Boolean = false): DiffResult {
        val repository = Repository(root)
        return TicketDiff.prepare(repository, Branches.read(repository)!!, id, uncommitted)
    }

    /** What the command for [ready] shows, run with [extra] (e.g. `--name-only`). */
    private fun GitRepo.show(ready: DiffResult.Ready, tool: Boolean = false, uncommitted: Boolean = false, vararg extra: String): String =
        git(*(TicketDiff.command(root, ready, tool, uncommitted = uncommitted).drop(1) + extra).toTypedArray())

    private fun String.files() = lines().filter { it.isNotBlank() }.sorted()

    @Test
    fun showsOnlyTheTicketsChangesAndTouchesNothing() {
        val (repo, wt) = withWork("diff-merge-base")
        val ready = assertIs<DiffResult.Ready>(repo.prepare("beta"))
        assertEquals("main", ready.target)
        assertEquals(listOf("code.txt", "tickets/beta.md"), repo.show(ready, extra = arrayOf("--name-only")).files(), "nothing new on main")
        assertEquals(listOf("difftool", "--dir-diff"), TicketDiff.command(repo.root, ready, tool = true).drop(3).take(2))

        assertEquals(0, run(repo, "diff", "beta", "--stat").statusCode)
        assertEquals("beta", repo.git("rev-parse", "--abbrev-ref", "HEAD", at = wt).trim())
        assertEquals("", repo.git("status", "--porcelain", at = wt).trim())
    }

    @Test
    fun uncommittedGoesUpToTheWorktreesFiles() {
        val (repo, wt) = withWork("diff-uncommitted")
        repo.write("code.txt", "one, then two\n", at = wt)
        val ready = assertIs<DiffResult.Ready>(repo.prepare("beta", uncommitted = true))
        assertEquals(listOf("code.txt", "tickets/beta.md"), repo.show(ready, uncommitted = true, extra = arrayOf("--name-only")).files())
        assertTrue("then two" in repo.show(ready, uncommitted = true))
        assertTrue("then two" !in repo.show(ready), "committed only by default")

        repo.git("worktree", "remove", "--force", wt.toString())
        val none = run(repo, "diff", "beta", "--uncommitted")
        assertEquals(1, none.statusCode)
        assertTrue("isn't checked out in any worktree" in none.stderr, none.stderr)
    }

    @Test
    fun refusesWhatHasNothingToShow() {
        val (repo, _) = withWork("diff-refused")
        val notStarted = run(repo, "diff", "alpha")
        assertEquals(1, notStarted.statusCode)
        assertTrue("No diff: 'alpha' has no branch: it isn't started" in notStarted.stderr, notStarted.stderr)

        repo.git("merge", "-q", "--no-ff", "-m", "merge beta", "beta")
        val merged = run(repo, "diff", "beta")
        assertEquals(1, merged.statusCode)
        assertTrue("merged into main already" in merged.stderr, merged.stderr)

        val both = run(repo, "diff", "beta", "--stat", "--tool")
        assertTrue("--stat and --tool don't go together" in both.stderr, both.stderr)
    }

    @Test
    fun worksInReviewModeOnThisWorktreesTicket() {
        val (repo, wt) = withWork("diff-in-review")
        assertEquals(0, run(repo, "review", "beta").statusCode)
        val ready = assertIs<DiffResult.Ready>(repo.prepare("beta"))
        assertEquals(wt, ready.worktree)
        assertEquals(listOf("code.txt", "tickets/beta.md"), repo.show(ready, extra = arrayOf("--name-only")).files())
        assertEquals(0, run(repo, "diff", "--stat", at = wt).statusCode, "no id: this worktree's ticket, in review mode")
    }
}
