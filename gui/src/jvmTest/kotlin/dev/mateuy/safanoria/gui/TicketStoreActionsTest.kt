package dev.mateuy.safanoria.gui

import dev.mateuy.safanoria.core.Status
import dev.mateuy.safanoria.gui.data.FinishOutcome
import dev.mateuy.safanoria.gui.data.StartOutcome
import dev.mateuy.safanoria.gui.data.TicketStore
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

    private fun repository(worktree: Boolean, vararg tickets: Pair<String, String>) {
        File(project, "safanoria.yaml").writeText("safanoria: 1\ndir: tickets\nmainBranch: main\n" + if (worktree) "worktree: ../project--{id}\n" else "")
        File(project, "tickets").mkdirs()
        tickets.forEach { (id, status) -> File(project, "tickets/$id.md").writeText(ticket(id, status = status).text) }
        git("init", "-q", "-b", "main")
        // The commits Start makes need an identity too.
        git("config", "user.name", "Test")
        git("config", "user.email", "test@example.com")
        git("add", ".")
        git("commit", "-q", "-m", "tickets")
    }

    private fun TicketStore.status(id: String) = snapshot.value.graph?.ticket(id)?.frontmatter?.status

    @Test
    fun startCreatesTheBranchAndWorktreeAndTheTicketIsInProgressThere() = runBlocking<Unit> {
        repository(worktree = true, "first-one" to "backlog")
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
    fun withoutAWorktreeSettingThisCheckoutSwitchesToTheBranch() = runBlocking<Unit> {
        repository(worktree = false, "first-one" to "ready")
        val store = TicketStore(root)

        assertEquals(StartOutcome.Started(root), store.start("first-one", today = "2026-10-05"))

        assertEquals("first-one", git("rev-parse", "--abbrev-ref", "HEAD"))
        assertEquals(Status.IN_PROGRESS, store.status("first-one"))
        assertEquals(root, store.workspace("first-one"))
    }

    @Test
    fun aTicketThatCannotBeStartedSaysWhyAndChangesNothing() = runBlocking<Unit> {
        repository(worktree = true, "closed-one" to "done")
        val store = TicketStore(root)

        val outcome = assertIs<StartOutcome.NotStarted>(store.start("closed-one"))

        assertEquals("'closed-one' is done; only backlog and ready tickets can be started", outcome.reason)
        assertEquals("main", git("branch", "--format=%(refname:short)"))
        assertIs<StartOutcome.NotStarted>(store.start("no-such-ticket"))
    }

    @Test
    fun finishSetsTheStartedTicketToReviewInItsWorktree() = runBlocking<Unit> {
        repository(worktree = true, "first-one" to "backlog")
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
    fun aTicketThatIsNotInProgressCannotBeFinished() = runBlocking<Unit> {
        repository(worktree = true, "first-one" to "backlog")
        val outcome = assertIs<FinishOutcome.NotFinished>(TicketStore(root).finish("first-one"))
        assertEquals("'first-one' is backlog; review needs in-progress", outcome.reason)
        assertEquals("tickets", git("log", "-1", "--format=%s"))
    }

    @Test
    fun aBranchNotCheckedOutHasNoWorkspace() = runBlocking<Unit> {
        repository(worktree = true, "first-one" to "backlog")
        git("branch", "first-one")
        assertNull(TicketStore(root).workspace("first-one"))
        assertNull(TicketStore(root).workspace("no-such-branch"))
    }
}
