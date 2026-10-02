package dev.mateuy.safanoria.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitTest {
    @Test
    fun branchesTreesAndBlobs() {
        val f = GitFixture("git-basics")
        f.write(CONFIG_FILE, GitFixture.CONFIG)
        f.write("tickets/one.md", "first ü\n")
        f.commit("one")
        f.checkout("feature", create = true)
        f.write("tickets/two.md", "second\n")
        f.commit("two")
        f.checkout("main")
        val git = f.repository.git

        assertEquals(listOf("feature", "main"), git.branches())
        assertEquals(setOf("main"), git.mergedInto("main"))
        assertEquals(setOf("feature", "main"), git.mergedInto("feature"))

        val tree = git.tree("feature", "tickets")!!
        assertEquals(listOf("one.md", "two.md"), tree.map { it.name })
        assertTrue(tree.all { it.type == "blob" && it.mode == "100644" })
        assertEquals("first ü\n", git.blob(tree[0].id))
        assertEquals(listOf(CONFIG_FILE, "tickets"), git.tree("main", "")!!.map { it.name })
        assertNull(git.tree("main", "nothing"))
        assertNull(git.tree("no-such-branch", "tickets"))

        assertTrue(git.hasPath("feature", "tickets/two.md"))
        assertFalse(git.hasPath("main", "tickets/two.md"))
        assertEquals(git.commitId("main"), git.mergeBase("main", "feature"))
        assertNotNull(git.commitId("feature"))
        assertNull(git.commitId("no-such-branch"))
    }

    @Test
    fun remoteBranchesAndWorktrees() {
        val origin = GitFixture("git-origin")
        origin.write(CONFIG_FILE, GitFixture.CONFIG)
        origin.commit("init")
        origin.checkout("shared", create = true)
        origin.checkout("main")

        val clone = GitFixture("git-clone")
        clone.git("remote", "add", "origin", origin.root.toString())
        clone.git("fetch", "-q", "origin")
        clone.git("checkout", "-q", "-b", "main", "origin/main")
        val wt = clone.worktree("work", create = true)
        val git = clone.repository.git

        assertEquals(listOf("main", "work"), git.branches())
        assertEquals(listOf("origin/main", "origin/shared"), git.remoteBranches())
        assertEquals(
            listOf(Worktree(clone.root, "main"), Worktree(wt, "work")),
            git.worktrees().map { Worktree(SystemFileSystem.canonicalize(it.path), it.branch) },
        )
        clone.git("checkout", "-q", "--detach", at = wt)
        assertEquals(null, git.worktrees()[1].branch)
    }
}
