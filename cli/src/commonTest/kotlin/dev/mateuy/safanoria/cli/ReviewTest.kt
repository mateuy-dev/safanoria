package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.testing.test
import okio.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Review mode: `review <id>` stages the ticket's whole diff in its worktree, `review --end` goes back keeping the edits. */
class ReviewTest {
    private fun run(repo: GitRepo, vararg args: String, at: Path = repo.root) =
        cli().test(listOf("--root", at.toString()) + args.toList())

    /** The scenario with beta's worktree and two commits of work there. */
    private fun withWork(name: String): Pair<GitRepo, Path> {
        val repo = GitRepo.scenario(name)
        val wt = repo.worktree("beta")
        repo.write("code.txt", "one\n", at = wt)
        repo.commit("beta work", at = wt)
        repo.write("more.txt", "more\n", at = wt)
        repo.commit("more work", at = wt)
        return repo to wt
    }

    private fun GitRepo.head(ref: String, at: Path = root) = git("rev-parse", ref, at = at).trim()
    private fun GitRepo.staged(at: Path) = git("diff", "--cached", "--name-only", at = at).lines().filter { it.isNotBlank() }.sorted()

    @Test
    fun reviewStagesTheWholeTicketAndEndKeepsOnlyTheEdits() {
        val (repo, wt) = withWork("review-round-trip")
        val tip = repo.head("beta")
        val r = run(repo, "review", "beta")
        assertEquals(0, r.statusCode, r.output)

        assertEquals("HEAD", repo.git("rev-parse", "--abbrev-ref", "HEAD", at = wt).trim())
        assertEquals(repo.head("main"), repo.head("HEAD", at = wt), "detached at the merge-base")
        assertEquals(tip, repo.head("beta"), "the branch doesn't move")
        assertEquals(listOf("code.txt", "more.txt", "tickets/beta.md"), repo.staged(wt))

        // The human edits, and even commits by mistake.
        repo.write("code.txt", "one, reviewed\n", at = wt)
        repo.commit("oops", at = wt)
        repo.write("note.txt", "// REVIEW: why?\n", at = wt)

        val e = run(repo, "review", "--end", at = wt)
        assertEquals(0, e.statusCode, e.output)
        assertEquals("beta", repo.git("rev-parse", "--abbrev-ref", "HEAD", at = wt).trim())
        assertEquals(tip, repo.head("beta"))
        assertTrue("  M  code.txt" in e.stdout && "  ?? note.txt" in e.stdout, e.stdout)
        assertEquals(listOf("?? note.txt", "M  code.txt"), repo.git("status", "--porcelain", at = wt).lines().filter { it.isNotBlank() }.sorted())
        assertEquals("one, reviewed\n", repo.read("code.txt", at = wt))
    }

    @Test
    fun inReviewModeOtherCommandsRefuseAndTheSessionIsTold() {
        val (repo, wt) = withWork("review-guards")
        assertEquals(0, run(repo, "review", "beta").statusCode)

        val again = run(repo, "review", "beta")
        assertEquals(1, again.statusCode)
        assertTrue("is in review mode for 'beta' already" in again.stderr, again.stderr)

        val finish = run(repo, "finish", "beta", "--date", "2026-10-05")
        assertEquals(1, finish.statusCode)
        assertTrue("'beta' is in review mode in" in finish.stderr && "review --end" in finish.stderr, finish.stderr)

        val context = run(repo, "context", at = wt)
        assertTrue("This worktree is in review mode" in context.stdout && "<ticket file=" in context.stdout, context.stdout)
        val codexContext = run(repo, "context", "--agent", "codex", at = wt)
        assertTrue("This worktree is in review mode" in codexContext.stdout && "ticket `beta`" in codexContext.stdout, codexContext.stdout)

        assertEquals(0, run(repo, "review", "--end", "beta").statusCode, "--end with an id, from another checkout")
        assertEquals(0, run(repo, "finish", "beta", "--date", "2026-10-05").statusCode)
        assertEquals(0, run(repo, "review", "beta").statusCode)
        val merge = run(repo, "merge", "beta", "--date", "2026-10-05")
        assertEquals(1, merge.statusCode)
        assertTrue("Not merged: 'beta' is in review mode in" in merge.stderr, merge.stderr)
    }

    @Test
    fun withoutAnIdItReviewsThisWorktreesTicket() {
        val (repo, wt) = withWork("review-current")
        val r = run(repo, "review", at = wt)
        assertEquals(0, r.statusCode, r.output)
        assertEquals(listOf("code.txt", "more.txt", "tickets/beta.md"), repo.staged(wt))

        val again = run(repo, "review", at = wt)
        assertEquals(1, again.statusCode)
        assertTrue("is in review mode for 'beta' already" in again.stderr, again.stderr)

        val main = run(repo, "review")
        assertTrue("missing argument <id>" in main.stderr, "main isn't a ticket and nothing may be asked: ${main.output}")
    }

    @Test
    fun reviewRefusesUncommittedChanges() {
        val (repo, wt) = withWork("review-uncommitted")
        repo.write("code.txt", "dirty\n", at = wt)
        val r = run(repo, "review", "beta")
        assertEquals(1, r.statusCode)
        assertTrue("uncommitted changes" in r.stderr, r.stderr)
        assertEquals("beta", repo.git("rev-parse", "--abbrev-ref", "HEAD", at = wt).trim())
    }

    @Test
    fun sinceReviewsOnlyTheLaterCommits() {
        val (repo, wt) = withWork("review-since")
        val r = run(repo, "review", "beta", "--since", "beta~1")
        assertEquals(0, r.statusCode, r.output)
        assertEquals(listOf("more.txt"), repo.staged(wt))
        assertTrue("after beta~1" in r.stdout, r.stdout)
    }

    @Test
    fun endOutsideReviewModeRefuses() {
        val (repo, wt) = withWork("review-end-none")
        val r = run(repo, "review", "--end", at = wt)
        assertEquals(1, r.statusCode)
        assertTrue("not in review mode: on branch 'beta'" in r.stderr, r.stderr)
    }

    @Test
    fun aMergedTicketHasNothingToReview() {
        val (repo, _) = withWork("review-merged")
        repo.git("merge", "-q", "--no-ff", "-m", "merge beta", "beta")
        val r = run(repo, "review", "beta")
        assertEquals(1, r.statusCode)
        assertTrue("merged into main already" in r.stderr, r.stderr)
    }

    /** A child that merged main when its parent didn't: main's changes would show as the child's. */
    @Test
    fun warnsAboutMergesFromOutsideTheTarget() {
        val repo = GitRepo("review-foreign")
        repo.write("safanoria.yaml", GitRepo.config("review-foreign"))
        repo.write("tickets/epic.md", GitRepo.ticket("epic"))
        repo.write("tickets/part.md", GitRepo.ticket("part").replace("size: S\n", "size: S\nparent: epic\n"))
        repo.commit("tickets")
        repo.checkout("epic", create = true)
        repo.write("tickets/epic.md", GitRepo.ticket("epic", "in-progress"))
        repo.commit("epic started")
        repo.checkout("part", create = true)
        repo.write("tickets/part.md", GitRepo.ticket("part", "in-progress").replace("size: S\n", "size: S\nparent: epic\n"))
        repo.commit("part started")
        repo.checkout("main")
        repo.write("main.txt", "main only\n")
        repo.commit("main moves on")
        repo.checkout("part")
        repo.git("merge", "-q", "--no-ff", "-m", "merge main", "main")
        repo.checkout("main")
        val wt = repo.worktree("part")

        val r = run(repo, "review", "part")
        assertEquals(0, r.statusCode, r.output)
        assertTrue("warning: 'part' merged in 1 commit that epic doesn't have" in r.stderr, r.stderr)
        assertTrue("main.txt" in repo.staged(wt), "against epic")
    }
}
