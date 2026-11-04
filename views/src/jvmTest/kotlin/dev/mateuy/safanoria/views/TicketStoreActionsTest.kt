package dev.mateuy.safanoria.views

import dev.mateuy.safanoria.core.Status
import kotlinx.coroutines.runBlocking
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Starting and finishing a ticket from the app, on a real git repository in a temporary directory. */
class TicketStoreActionsTest {
    private val base: File = Files.createTempDirectory("safanoria-gui").toRealPath().toFile()
    private val project = File(base, "project").apply { mkdirs() }
    private val root: Path = project.toOkioPath()

    @AfterTest
    fun cleanUp() {
        base.deleteRecursively()
    }

    private fun git(vararg args: String, dir: File = project): String {
        val process = ProcessBuilder(listOf("git", "-c", "user.name=Test", "-c", "user.email=test@example.com") + args)
            .directory(dir).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "git ${args.joinToString(" ")}: $output" }
        return output.trim()
    }

    private fun repository(vararg tickets: Pair<String, String>) {
        File(project, "safanoria.yaml").writeText("safanoria: 1\ndir: tickets\nmainBranch: main\nworktree: ../project--{id}\n")
        File(project, "tickets").mkdirs()
        tickets.forEach { (id, status) -> File(project, "tickets/$id.md").writeText(ticket(id, status = status).text) }
        git("init", "-q", "-b", "main")
        // The commits Start makes need an identity too.
        git("config", "user.name", "Test")
        git("config", "user.email", "test@example.com")
        // Git for Windows checks files out with CRLF by default; the tests compare file contents.
        git("config", "core.autocrlf", "false")
        git("add", ".")
        git("commit", "-q", "-m", "tickets")
    }

    private fun TicketStore.status(id: String) = snapshot.value.graph?.ticket(id)?.frontmatter?.status

    @Test
    fun startCreatesTheBranchAndWorktreeAndTheTicketIsInProgressThere() = runBlocking<Unit> {
        repository("first-one" to "backlog")
        val store = TicketStore(root)

        val outcome = store.start("first-one", today = "2026-10-05")

        val workspace = File(base, "project--first-one")
        assertEquals(StartOutcome.Started(workspace.toOkioPath()), outcome)
        assertEquals("first-one", git("rev-parse", "--abbrev-ref", "HEAD", dir = workspace))
        assertEquals("first-one: start", git("log", "-1", "--format=%s", "first-one"))
        assertTrue("- **2026-10-05** · status · started" in File(workspace, "tickets/first-one.md").readText())
        // The tickets were read again: the board shows it started, from its branch.
        assertEquals(Status.IN_PROGRESS, store.status("first-one"))
        assertEquals(workspace.toOkioPath(), store.workspace("first-one"))
        // This checkout didn't move.
        assertEquals("main", git("rev-parse", "--abbrev-ref", "HEAD"))
    }

    @Test
    fun aTicketThatCannotBeStartedSaysWhyAndChangesNothing() = runBlocking<Unit> {
        repository("closed-one" to "done")
        val store = TicketStore(root)

        val outcome = assertIs<StartOutcome.NotStarted>(store.start("closed-one"))

        assertEquals("'closed-one' is done; only backlog and ready tickets can be started", outcome.reason)
        assertEquals("main", git("branch", "--format=%(refname:short)"))
        assertIs<StartOutcome.NotStarted>(store.start("no-such-ticket"))
    }

    /**
     * Parent big in progress in its worktree, component app at 1.2.0; child one done and merged
     * on big, child two not done. Returns big's worktree.
     */
    private fun parentWithAChildDone(): File {
        File(project, "safanoria.yaml").writeText(
            "safanoria: 1\ndir: tickets\nmainBranch: main\nworktree: ../project--{id}\n" +
                "components:\n  app:\n    version: { file: gradle.properties, property: version }\n",
        )
        File(project, "gradle.properties").writeText("version=1.2.0\n")
        File(project, "tickets").mkdirs()
        fun child(id: String, status: String) = ticket(id, status = status, extra = "parent: big\narea: [app]").text + "- **2026-10-02** · status · $status\n"
        File(project, "tickets/big.md").writeText(ticket("big", status = "in-progress", plan = "- [ ] `one`: One\n- [ ] `two`: Two").text)
        File(project, "tickets/one.md").writeText(child("one", "backlog"))
        File(project, "tickets/two.md").writeText(child("two", "backlog"))
        git("init", "-q", "-b", "main")
        git("config", "user.name", "Test")
        git("config", "user.email", "test@example.com")
        git("config", "core.autocrlf", "false")
        git("add", ".")
        git("commit", "-q", "-m", "tickets")
        val workspace = File(base, "project--big")
        git("worktree", "add", "-q", "-b", "big", workspace.path)
        File(workspace, "tickets/one.md").writeText(child("one", "done"))
        File(workspace, "tickets/big.md").writeText(File(workspace, "tickets/big.md").readText().replace("- [ ] `one`", "- [x] `one`"))
        File(workspace, "one.txt").writeText("one\n")
        git("add", ".", dir = workspace)
        git("commit", "-q", "-m", "one: merge (done)", dir = workspace)
        return workspace
    }

    @Test
    fun shipAsksWhatShipsAndShipsItStampedAndMergedBack() = runBlocking<Unit> {
        val workspace = parentWithAChildDone()
        val store = TicketStore(root)
        store.refresh()
        assertTrue(TicketAction.SHIP in ticketViewState("big", store.snapshot.value).actions)

        val form = assertIs<ShipCheck.Ready>(store.prepareShip("big")).form
        assertEquals(listOf("one"), form.children)
        assertEquals(listOf(ShipComponent("app", external = false, fromSource = "1.2.0")), form.components)

        val outcome = store.ship("big", form.toggleStamp().releases, today = "2026-10-06").let { assertIs<ShipOutcome.Shipped>(it, it.toString()) }

        assertEquals(listOf("app 1.2.0: stamped one"), outcome.stamped)
        assertEquals("big: ship one (app 1.2.0)", git("log", "-1", "--format=%s", "main"))
        assertEquals("one\n", File(project, "one.txt").readText())
        assertTrue("app: 1.2.0" in File(project, "tickets/one.md").readText())
        assertEquals("big: merge main back", git("log", "-1", "--format=%s", "big"))
        assertTrue("· ship · shipped to main: `one`, released in app 1.2.0" in File(workspace, "tickets/big.md").readText())
        assertEquals(Status.IN_PROGRESS, store.status("big"))
        // Nothing new to ship: the reason is told.
        assertTrue("nothing to ship" in assertIs<ShipCheck.Refused>(store.prepareShip("big")).reason)
    }

    @Test
    fun shipWithoutStampingOnlyLogsAndANonParentIsRefused() = runBlocking<Unit> {
        parentWithAChildDone()
        val store = TicketStore(root)

        assertTrue("not a parent" in assertIs<ShipCheck.Refused>(store.prepareShip("two")).reason)
        store.ship("big", emptyList(), today = "2026-10-06").let { assertIs<ShipOutcome.Shipped>(it, it.toString()) }

        assertTrue("resolvedIn" !in File(project, "tickets/one.md").readText())
        assertEquals("big: ship one", git("log", "-1", "--format=%s", "main"))
    }

    @Test
    fun finishSetsTheStartedTicketToReviewInItsWorktree() = runBlocking<Unit> {
        repository("first-one" to "backlog")
        val store = TicketStore(root)
        store.start("first-one", today = "2026-10-05")

        assertEquals(FinishOutcome.Finished("first-one"), store.finish("first-one", today = "2026-10-06"))

        val workspace = File(base, "project--first-one")
        assertEquals("first-one: review", git("log", "-1", "--format=%s", "first-one"))
        assertTrue("- **2026-10-06** · status · review" in File(workspace, "tickets/first-one.md").readText())
        assertEquals("", git("status", "--porcelain", dir = workspace))
        assertEquals(Status.REVIEW, store.status("first-one"))
    }

    @Test
    fun finishRefusesUncommittedWorkInTheWorktree() = runBlocking<Unit> {
        repository("first-one" to "backlog")
        val store = TicketStore(root)
        store.start("first-one", today = "2026-10-05")
        File(base, "project--first-one/code.txt").writeText("work\n")

        val outcome = assertIs<FinishOutcome.NotFinished>(store.finish("first-one", today = "2026-10-06"))

        assertTrue("uncommitted changes (1 file)" in outcome.reason, outcome.reason)
        assertEquals(Status.IN_PROGRESS, store.status("first-one"))
    }

    @Test
    fun mergeLandsATicketInReviewAndRemovesItsWorktreeAndBranch() = runBlocking<Unit> {
        repository("first-one" to "backlog")
        val store = TicketStore(root)
        store.start("first-one", today = "2026-10-05")
        val workspace = File(base, "project--first-one")
        File(workspace, "code.txt").writeText("work\n")
        git("add", ".", dir = workspace)
        git("commit", "-q", "-m", "work", dir = workspace)
        assertIs<MergeOutcome.NotMerged>(store.merge("first-one", today = "2026-10-06"))
        store.finish("first-one", today = "2026-10-06")

        val outcome = assertIs<MergeOutcome.Merged>(store.merge("first-one", today = "2026-10-07"))

        assertEquals("main", outcome.target)
        assertEquals("first-one: merge (done)", git("log", "-1", "--format=%s"))
        assertEquals("work\n", File(project, "code.txt").readText())
        assertTrue("- **2026-10-07** · status · done" in File(project, "tickets/first-one.md").readText())
        assertTrue(!workspace.exists())
        assertEquals("main", git("branch", "--format=%(refname:short)"))
        assertEquals(Status.DONE, store.status("first-one"))
    }

    @Test
    fun reopenSendsATicketInReviewBackWithTheReason() = runBlocking<Unit> {
        repository("first-one" to "backlog")
        val store = TicketStore(root)
        store.start("first-one", today = "2026-10-05")
        store.finish("first-one", today = "2026-10-06")

        assertIs<ReopenOutcome.NotReopened>(store.reopen("first-one", "  ", today = "2026-10-07"))
        assertEquals(ReopenOutcome.Reopened("first-one"), store.reopen("first-one", "The list isn't sorted", today = "2026-10-07"))

        val text = File(base, "project--first-one/tickets/first-one.md").readText()
        assertTrue("- **2026-10-07** · status · reopened: The list isn't sorted" in text, text)
        assertEquals(Status.IN_PROGRESS, store.status("first-one"))
    }

    @Test
    fun aTicketThatIsNotInProgressCannotBeFinished() = runBlocking<Unit> {
        repository("first-one" to "backlog")
        val outcome = assertIs<FinishOutcome.NotFinished>(TicketStore(root).finish("first-one"))
        assertEquals("'first-one' is backlog; review needs in-progress", outcome.reason)
        assertEquals("tickets", git("log", "-1", "--format=%s"))
    }

    @Test
    fun aTicketStartedOnAnotherComputerIsFetchedAndGetsItsWorktreeHere() = runBlocking<Unit> {
        // project is the other computer (and the remote): first-one is in progress on its branch there.
        repository("first-one" to "backlog")
        git("checkout", "-q", "-b", "first-one")
        File(project, "tickets/first-one.md").writeText(ticket("first-one", status = "in-progress").text)
        git("commit", "-q", "-am", "first-one: start")
        git("checkout", "-q", "main")
        git("clone", "-q", project.path, "here", dir = base)
        val here = File(base, "here")
        val store = TicketStore(here.toOkioPath())
        store.refresh()
        assertEquals(Status.IN_PROGRESS, store.status("first-one"))
        assertEquals("origin/first-one", store.snapshot.value.graph?.ticket("first-one")?.remoteBranch)

        val notStarted = assertIs<StartOutcome.NotStarted>(store.start("first-one"))
        assertTrue("started on another computer" in notStarted.reason, notStarted.reason)

        // More work pushed there shows once fetched, not before.
        git("checkout", "-q", "first-one")
        File(project, "tickets/first-one.md").writeText(ticket("first-one", status = "review").text)
        git("commit", "-q", "-am", "first-one: review")
        git("checkout", "-q", "main")
        store.refresh()
        assertEquals(Status.IN_PROGRESS, store.status("first-one"))
        store.fetch()
        assertEquals(Status.REVIEW, store.status("first-one"))
        assertNull(store.snapshot.value.error)

        val workspace = File(base, "project--first-one")
        assertEquals(StartOutcome.Started(workspace.toOkioPath()), store.checkOut("first-one"))
        assertEquals("origin/first-one", git("rev-parse", "--abbrev-ref", "first-one@{upstream}", dir = here))
        assertEquals("first-one: review", git("log", "-1", "--format=%s", dir = workspace))
        assertNull(store.snapshot.value.graph?.ticket("first-one")?.remote)
        assertIs<StartOutcome.NotStarted>(store.checkOut("first-one"))
    }

    @Test
    fun localBranchWithoutWorktreeOffersCheckoutAndThenOpenTerminal() = runBlocking<Unit> {
        repository("first-one" to "backlog")
        git("checkout", "-q", "-b", "first-one")
        File(project, "tickets/first-one.md").writeText(ticket("first-one", status = "in-progress").text)
        git("commit", "-q", "-am", "started elsewhere")
        git("checkout", "-q", "main")
        val store = TicketStore(root)
        store.refresh()
        assertEquals(listOf(TicketAction.CHECK_OUT, TicketAction.DIFF), ticketViewState("first-one", store.snapshot.value).actions)
        assertIs<StartOutcome.Started>(store.checkOut("first-one"))
        assertEquals(listOf(TicketAction.OPEN_TERMINAL, TicketAction.DIFF, TicketAction.FINISH), ticketViewState("first-one", store.snapshot.value).actions)
        assertEquals("started elsewhere", git("log", "-1", "--format=%s", "first-one"))
    }

    @Test
    fun aBranchNotCheckedOutHasNoWorkspace() = runBlocking<Unit> {
        repository("first-one" to "backlog")
        git("branch", "first-one")
        assertNull(TicketStore(root).workspace("first-one"))
        assertNull(TicketStore(root).workspace("no-such-branch"))
    }
}
