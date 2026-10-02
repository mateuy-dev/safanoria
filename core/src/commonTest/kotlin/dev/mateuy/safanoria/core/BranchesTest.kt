package dev.mateuy.safanoria.core

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BranchesTest {
    private fun t(id: String, status: String = "backlog", parent: String? = null) = GitFixture.ticket(id, status, parent)

    /** id → "status@branch", with `!` when it is only on that branch. */
    private fun Branches.summary(): Map<String, String> =
        tickets.associate { it.fileId to "${it.frontmatter!!.status!!.text}@${it.branch}${if (it.onlyOnBranch) "!" else ""}" }

    /**
     * main: unstarted, started, checked-out, kept, parent, only-main.
     * - started: in progress on its branch.
     * - checked-out: on a branch in a worktree, with an uncommitted edit.
     * - kept: merged into main, then done on main; its branch is kept.
     * - parent: in progress on its branch, which has its children:
     *   parent-merged (merged into the parent's branch, then done there) and
     *   parent-open (in progress on its own branch, created from the parent's).
     * - feature: has stray (only there) and twice; other: has twice too (created separately).
     */
    private fun scenario(): GitFixture {
        val f = GitFixture("branches")
        f.write(CONFIG_FILE, GitFixture.CONFIG)
        for (id in listOf("unstarted", "started", "checked-out", "kept", "parent")) f.write("tickets/$id.md", t(id))
        f.commit("tickets")

        f.checkout("started", create = true)
        f.write("tickets/started.md", t("started", "in-progress")); f.commit("started")

        f.checkout("kept", create = true, at = f.root)
        f.git("reset", "-q", "--hard", "main")
        f.write("tickets/kept.md", t("kept", "review")); f.commit("kept")
        f.checkout("main")
        f.merge("kept")
        f.write("tickets/kept.md", t("kept", "done").replace("- [ ] Step", "- [x] Step").replace("- [ ] Done", "- [x] Done")); f.commit("kept done")

        f.checkout("parent", create = true)
        f.write("tickets/parent.md", t("parent", "in-progress"))
        f.write("tickets/parent-merged.md", t("parent-merged", parent = "parent"))
        f.write("tickets/parent-open.md", t("parent-open", parent = "parent"))
        f.commit("children")
        f.checkout("parent-merged", create = true)
        f.write("tickets/parent-merged.md", t("parent-merged", "review", parent = "parent")); f.commit("child work")
        f.checkout("parent")
        f.merge("parent-merged")
        f.write("tickets/parent-merged.md", t("parent-merged", "done", parent = "parent").replace("- [ ] Step", "- [x] Step")); f.commit("child done")
        f.checkout("parent-open", create = true)
        f.write("tickets/parent-open.md", t("parent-open", "in-progress", parent = "parent")); f.commit("child started")

        f.checkout("main")
        f.checkout("feature", create = true)
        f.write("tickets/stray.md", t("stray")); f.write("tickets/twice.md", t("twice")); f.commit("found work")
        f.checkout("main")
        f.checkout("other", create = true)
        f.write("tickets/twice.md", t("twice", "ready")); f.commit("same id")
        f.checkout("main")
        f.write("tickets/only-main.md", t("only-main")); f.commit("later on main")

        val wt = f.worktree("checked-out", create = true)
        f.write("tickets/checked-out.md", t("checked-out", "in-progress"), at = wt) // uncommitted
        return f
    }

    @Test
    fun realCopies() {
        val f = scenario()
        val branches = assertNotNull(Branches.read(f.repository))
        assertEquals(
            mapOf(
                "unstarted" to "backlog@main",
                "started" to "in-progress@started",
                "checked-out" to "in-progress@checked-out",
                "kept" to "done@main",
                "parent" to "in-progress@parent",
                "parent-merged" to "done@parent",
                "parent-open" to "in-progress@parent-open",
                "stray" to "backlog@feature!",
                "twice" to "backlog@feature!",
                "only-main" to "backlog@main",
            ),
            branches.summary(),
        )
        // Paths are as in this checkout, whichever branch or worktree the copy came from.
        assertEquals(f.root / "tickets" / "checked-out.md", branches.tickets.first { it.fileId == "checked-out" }.path)
        assertEquals(listOf(CreatedTwice("twice", "feature", "other")), branches.createdTwice())
    }

    @Test
    fun fromAnotherBranchTheResultIsTheSame() {
        val f = scenario()
        f.checkout("started")
        val branches = assertNotNull(Branches.read(f.repository))
        assertEquals("in-progress@started", branches.summary()["started"])
        assertEquals("backlog@main", branches.summary()["only-main"])
        assertEquals("started", branches.branches.first())
    }

    @Test
    fun remoteBranchesOnlyWhenAsked() {
        val origin = scenario()
        val clone = GitFixture("branches-clone")
        clone.git("remote", "add", "origin", origin.root.toString())
        clone.git("fetch", "-q", "origin")
        clone.git("checkout", "-q", "-b", "main", "origin/main")

        assertEquals("backlog@main", Branches.read(clone.repository)!!.summary()["started"])
        val all = Branches.read(clone.repository, remote = true)!!
        assertEquals("in-progress@started", all.summary()["started"])
        assertEquals("backlog@feature!", all.summary()["stray"])
        assertEquals(listOf(CreatedTwice("twice", "feature", "other")), all.createdTwice())
    }

    @Test
    fun nothingToReadAcross() {
        val fs = FakeFileSystem()
        fs.createDirectories("/repo".toPath())
        fs.write("/repo/safanoria.yaml".toPath()) { writeUtf8(GitFixture.CONFIG) }
        assertNull(Branches.read(Repository("/repo".toPath(), fs)), "not a git repository")

        val f = GitFixture("branches-no-main")
        f.write(CONFIG_FILE, GitFixture.CONFIG + "mainBranch: trunk\n")
        f.commit("init")
        assertNull(Branches.read(f.repository), "no mainBranch branch")
    }
}
