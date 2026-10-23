package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.testing.test
import okio.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `diff <id>`: the ticket's changes from its merge-base with the target, touching nothing. */
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

    /** The files the command printed by --dry-run changes, run with --name-only. */
    private fun GitRepo.changed(command: String): List<String> =
        git(*(command.trim().split(" ").drop(1) + "--name-only").toTypedArray()).lines().filter { it.isNotBlank() }.sorted()

    @Test
    fun showsOnlyTheTicketsChangesAndTouchesNothing() {
        val (repo, wt) = withWork("diff-merge-base")
        val dry = run(repo, "diff", "beta", "--dry-run")
        assertEquals(0, dry.statusCode, dry.output)
        assertTrue(dry.stdout.trim().endsWith(" beta") && " diff " in dry.stdout, dry.stdout)
        assertEquals(listOf("code.txt", "tickets/beta.md"), repo.changed(dry.stdout), "nothing new on main")

        assertEquals(0, run(repo, "diff", "beta", "--stat").statusCode)
        assertEquals("beta", repo.git("rev-parse", "--abbrev-ref", "HEAD", at = wt).trim())
        assertEquals("", repo.git("status", "--porcelain", at = wt).trim())

        val tool = run(repo, "diff", "beta", "--tool", "--dry-run")
        assertTrue(" difftool --dir-diff " in tool.stdout, tool.stdout)
    }

    @Test
    fun uncommittedGoesUpToTheWorktreesFiles() {
        val (repo, wt) = withWork("diff-uncommitted")
        repo.write("code.txt", "one, then two\n", at = wt)
        val dry = run(repo, "diff", "--uncommitted", "--dry-run", at = wt)
        assertEquals(0, dry.statusCode, dry.output)
        assertTrue("-C $wt diff " in dry.stdout, "this worktree's ticket, run there: ${dry.stdout}")
        assertEquals(listOf("code.txt", "tickets/beta.md"), repo.changed(dry.stdout))
        assertTrue("then two" in repo.git(*(dry.stdout.trim().split(" ").drop(1)).toTypedArray()))

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
    fun worksInReviewMode() {
        val (repo, wt) = withWork("diff-in-review")
        assertEquals(0, run(repo, "review", "beta").statusCode)
        val dry = run(repo, "diff", "--dry-run", at = wt)
        assertEquals(0, dry.statusCode, dry.output)
        assertEquals(listOf("code.txt", "tickets/beta.md"), repo.changed(dry.stdout))
    }
}
