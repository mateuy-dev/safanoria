package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.testing.test
import dev.mateuy.safanoria.core.Branches
import dev.mateuy.safanoria.core.Repository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `ship`: the partial release of a parent (SPEC §8.1). */
class ShipTest {
    private fun run(repo: GitRepo, vararg args: String) =
        cli().test(listOf("--root", repo.root.toString()) + args.toList() + listOf("--date", "2026-10-05"))

    private fun child(id: String, status: String) =
        GitRepo.ticket(id, status).replace("updated: 2026-10-02\n", "updated: 2026-10-02\nparent: big\narea: [app]\n")

    /**
     * main: big (a parent) with children one and two. On branch big, in its worktree: big
     * in-progress, one done and merged there, two in-progress on its own branch.
     */
    private fun scenario(name: String): Pair<GitRepo, okio.Path> {
        val repo = GitRepo(name)
        repo.write("safanoria.yaml", GitRepo.config(name))
        repo.write("tickets/big.md", GitRepo.ticket("big").replace("- [ ] Step", "- [ ] `one`: One\n- [ ] `two`: Two\n- [ ] Own step"))
        repo.write("tickets/one.md", child("one", "backlog"))
        repo.write("tickets/two.md", child("two", "backlog"))
        repo.commit("tickets")
        repo.git("branch", "big")
        val wt = repo.worktree("big")
        repo.write("tickets/big.md", repo.read("tickets/big.md", at = wt).replace("status: backlog", "status: in-progress"), at = wt)
        repo.write("tickets/two.md", child("two", "in-progress"), at = wt)
        repo.commit("big started", at = wt)
        repo.git("branch", "two", "big")
        // one: done and merged into big, checked in its Plan.
        repo.write("tickets/one.md", child("one", "done"), at = wt)
        repo.write("tickets/big.md", repo.read("tickets/big.md", at = wt).replace("- [ ] `one`", "- [x] `one`"), at = wt)
        repo.write("one.txt", "one\n", at = wt)
        repo.commit("one: merge (done)", at = wt)
        return repo to wt
    }

    @Test
    fun shipMergesIntoMainAndBackWithoutStamping() {
        val (repo, wt) = scenario("ship-flow")
        val r = run(repo, "ship", "big")
        assertEquals(0, r.statusCode, r.output)
        assertTrue("nothing was pushed" in r.stdout, r.stdout)

        assertEquals("big: ship one", repo.git("log", "-1", "--format=%s", "main").trim())
        assertEquals("one\n", repo.read("one.txt"), "main is checked out here and moved")
        assertTrue("status: done" in repo.read("tickets/one.md"))
        assertTrue("resolvedIn" !in repo.read("tickets/one.md"), "ship doesn't stamp: release does")
        assertTrue("status: in-progress" in repo.read("tickets/big.md"))
        assertTrue("· ship · shipped to main: `one`\n" in repo.read("tickets/big.md"))

        // The parent's branch is not merged into main: still the children's target.
        assertEquals("big: merge main back", repo.git("log", "-1", "--format=%s", "big").trim())
        assertTrue("shipped to main" in repo.read("tickets/big.md", at = wt), "the worktree moved too")
        assertEquals("", repo.git("status", "--porcelain", at = wt).trim())
        assertEquals(1, repo.git("rev-list", "--count", "main..big").trim().toInt(), "big has a commit main lacks")
        val branches = Branches.read(Repository(repo.root))!!
        assertEquals("big", branches.targetBranch("two"))
        assertEquals("big", branches.graph.ticket("one")!!.branch)

        // Nothing new to ship until another child is done.
        val again = run(repo, "ship", "big")
        assertEquals(1, again.statusCode, again.output)
        assertTrue("nothing to ship" in again.stderr, again.stderr)
    }

    @Test
    fun releaseStampsShippedChildrenAndMergesBack() {
        val (repo, wt) = scenario("ship-release")
        assertEquals(0, run(repo, "ship", "big").statusCode)
        val big = repo.git("rev-parse", "big").trim()

        val dry = run(repo, "release", "app", "1.2.0", "--dry-run")
        assertTrue("would merge main, with the stamps, back into 'big'" in dry.stdout, dry.output)
        assertEquals(big, repo.git("rev-parse", "big").trim())

        val r = run(repo, "release", "app", "1.2.0")
        assertEquals(0, r.statusCode, r.output)
        assertTrue("merged main, with the stamps, back into 'big'" in r.stdout, r.stdout)
        // On main the stamps are uncommitted, for the release commit; on big they are merged.
        assertTrue("resolvedIn:\n  app: 1.2.0" in repo.read("tickets/one.md"), repo.read("tickets/one.md"))
        assertEquals("big: merge main back (released app 1.2.0)", repo.git("log", "-1", "--format=%s", "big").trim())
        assertTrue("app: 1.2.0" in repo.read("tickets/one.md", at = wt), "the worktree moved too")
        assertEquals("", repo.git("status", "--porcelain", at = wt).trim())
        val branches = Branches.read(Repository(repo.root))!!
        assertEquals("big", branches.graph.ticket("one")!!.branch)
        assertTrue("app: 1.2.0" in branches.graph.ticket("one")!!.text)

        // The release commit makes the same changes: merging it into big later is clean.
        repo.commit("Release 1.2.0")
        repo.git("merge", "-q", "--no-edit", "main", at = wt)
        assertTrue("app: 1.2.0" in repo.read("tickets/one.md", at = wt))
    }

    @Test
    fun releaseSkipsADirtyParentBranch() {
        val (repo, wt) = scenario("ship-release-dirty")
        assertEquals(0, run(repo, "ship", "big").statusCode)
        val big = repo.git("rev-parse", "big").trim()
        repo.write("scratch.txt", "x\n", at = wt)

        val r = run(repo, "release", "app", "1.2.0")
        assertEquals(0, r.statusCode, r.output)
        assertTrue("warning: 'big' not merged back" in r.stderr, r.stderr)
        assertTrue("uncommitted changes" in r.stderr, r.stderr)
        assertTrue("app: 1.2.0" in repo.read("tickets/one.md"), "the release itself goes on")
        assertEquals(big, repo.git("rev-parse", "big").trim())
    }

    @Test
    fun shipRefusesAndChangesNothing() {
        val (repo, wt) = scenario("ship-refuses")
        val main = repo.git("rev-parse", "main").trim()
        val big = repo.git("rev-parse", "big").trim()

        assertTrue("would ship 'big' to main with one" in run(repo, "ship", "big", "--dry-run").stdout)

        repo.write("scratch.txt", "x\n", at = wt)
        val dirty = run(repo, "ship", "big")
        assertTrue("uncommitted changes (1 file)" in dirty.stderr, dirty.stderr)
        repo.git("clean", "-fq", at = wt)

        assertTrue("is not a parent" in run(repo, "ship", "one").stderr)
        assertEquals(main, repo.git("rev-parse", "main").trim())

        repo.write("one.txt", "main\n")
        repo.commit("main work")
        val conflict = run(repo, "ship", "big")
        assertEquals(1, conflict.statusCode, conflict.output)
        assertTrue("conflicts in one.txt" in conflict.stderr, conflict.stderr)
        assertEquals(big, repo.git("rev-parse", "big").trim())
        assertTrue(main != repo.git("rev-parse", "main").trim()) // only "main work"
    }
}
