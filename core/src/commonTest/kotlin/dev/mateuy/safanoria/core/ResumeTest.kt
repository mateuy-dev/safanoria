package dev.mateuy.safanoria.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ResumeTest {
    private fun t(id: String, status: String = "backlog", parent: String? = null) = GitFixture.ticket(id, status, parent)

    /**
     * main: parent, solo, waiting, idle.
     * - parent: in progress on its branch, with child-open (in progress on its branch, in a
     *   worktree, one uncommitted file) and child-later (backlog).
     * - solo: in progress on its branch, no worktree, first Plan item done.
     * - waiting: in review on its branch. idle: backlog, no branch.
     */
    private fun scenario(name: String): GitFixture {
        val f = GitFixture(name)
        f.write(CONFIG_FILE, GitFixture.CONFIG)
        for (id in listOf("parent", "solo", "waiting", "idle")) f.write("tickets/$id.md", t(id))
        f.commit("tickets")

        f.checkout("solo", create = true)
        f.write("tickets/solo.md", t("solo", "in-progress").replace("- [ ] Step", "- [x] First\n- [ ] Second")); f.commit("solo")
        f.checkout("main")
        f.checkout("waiting", create = true)
        f.write("tickets/waiting.md", t("waiting", "review")); f.commit("waiting")
        f.checkout("main")

        f.checkout("parent", create = true)
        f.write("tickets/parent.md", t("parent", "in-progress").replace("- [ ] Step", "- [ ] `child-open`\n- [ ] `child-later`"))
        f.write("tickets/child-open.md", t("child-open", parent = "parent"))
        f.write("tickets/child-later.md", t("child-later", parent = "parent"))
        f.commit("children")
        f.git("branch", "child-open")
        f.checkout("main")

        val wt = f.worktree("child-open")
        f.write("tickets/child-open.md", t("child-open", "in-progress", parent = "parent"), at = wt)
        f.commit("child-open started", at = wt)
        f.write("notes.txt", "work in progress", at = wt) // uncommitted
        return f
    }

    private fun Resume.ids(query: String? = null) = candidates(query).map { it.fileId }

    private fun resume(f: GitFixture, at: Repository = f.repository) = Resume(at, assertNotNull(Branches.read(at)).graph)

    @Test
    fun withoutQueryEveryTicketInProgressAndChildrenInsteadOfParents() {
        val r = resume(scenario("resume-all"))
        assertEquals(listOf("child-open", "solo"), r.ids())
    }

    @Test
    fun aParentLeadsToItsChildInProgress() {
        val r = resume(scenario("resume-parent"))
        assertEquals(listOf("child-open"), r.ids("parent"))
        assertEquals(listOf("child-open"), r.ids("child-open"))
        assertEquals(listOf("idle"), r.ids("idle")) // an id is taken as it is, whatever its status
    }

    @Test
    fun wordsMatchIdsAndTitlesOfActiveTickets() {
        val r = resume(scenario("resume-words"))
        assertEquals(listOf("waiting"), r.ids("TICKET wait"))
        assertEquals(listOf("child-open"), r.ids("ticket parent")) // parent matches, then its child
        assertEquals(emptyList(), r.ids("ticket idl")) // backlog isn't active
        assertEquals(emptyList(), r.ids("nothing like it"))
    }

    @Test
    fun pointsGiveBranchWorktreeAndWhatIsNext() {
        val f = scenario("resume-points")
        val r = resume(f)
        val child = r.point(r.candidates("child-open").single())
        assertEquals("child-open", child.branch)
        assertEquals(SystemFileSystem.canonicalize(f.root.parent!! / "resume-points--child-open"), child.worktree)
        assertEquals(1, child.uncommitted)
        assertEquals("Step", child.next?.text)
        assertEquals("started", child.lastLog?.text)

        val solo = r.point(r.candidates("solo").single())
        assertEquals("solo", solo.branch)
        assertNull(solo.worktree)
        assertNull(solo.uncommitted)
        assertEquals("Second", solo.next?.text)

        assertNull(r.point(r.candidates("idle").single()).branch)
    }

    @Test
    fun fromAWorktreeTheAnswerIsTheSame() {
        val f = scenario("resume-from-worktree")
        val wt = Repository(SystemFileSystem.canonicalize(f.root.parent!! / "resume-from-worktree--child-open"))
        val r = resume(f, wt)
        assertEquals(listOf("child-open", "solo"), r.ids())
        assertEquals(wt.root, r.point(r.candidates("parent").single()).worktree)
        f.checkout("solo")
        assertEquals(f.root, resume(f).point(resume(f).candidates("solo").single()).worktree)
    }
}
