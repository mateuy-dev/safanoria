package dev.mateuy.safanoria.gui

import dev.mateuy.safanoria.core.SessionAgent
import dev.mateuy.safanoria.core.Status
import dev.mateuy.safanoria.views.TicketAction
import dev.mateuy.safanoria.views.TicketStore
import kotlinx.coroutines.*
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File
import java.nio.file.Files
import kotlin.test.*
import dev.mateuy.safanoria.gui.data.Terminal
import dev.mateuy.safanoria.gui.ticket.TicketViewModel
import javax.swing.SwingUtilities

class AgentSessionTest {
    private val base = Files.createTempDirectory("safanoria-agent-ui").toRealPath().toFile()
    private val root = File(base, "project").apply { mkdirs() }
    private fun git(vararg args: String): String {
        val p = ProcessBuilder(listOf("git") + args).directory(root).redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().readText()
        check(p.waitFor() == 0) { text }
        return text.trim()
    }
    private suspend fun project(default: String? = null): TicketStore {
        File(root, "safanoria.yaml").writeText("safanoria: 1\nworktree: ../work--{id}\n" + (default?.let { "defaultAgent: $it\n" } ?: ""))
        File(root, "tickets").mkdirs()
        File(root, "tickets/example.md").writeText("""
            ---
            id: example
            type: feature
            title: Example
            status: backlog
            priority: medium
            size: L
            created: 2026-10-29
            updated: 2026-10-29
            ---

            ## Objective

            Exercise an agent session.

            ## Work Log
        """.trimIndent() + "\n")
        git("init", "-q", "-b", "main")
        git("config", "user.name", "Test")
        git("config", "user.email", "test@example.com")
        git("add", ".")
        git("commit", "-qm", "Initial")
        return TicketStore(root.toOkioPath()).also { it.refresh() }
    }
    @AfterTest fun cleanUp() { base.deleteRecursively() }
    private class FakeTerminal : Terminal {
        var available = true
        var problem: String? = null
        val opened = mutableListOf<Pair<Path, List<String>>>()
        override fun has(program: String) = available
        override fun open(directory: Path, command: List<String>): String? {
            opened += directory to command
            return problem
        }
    }

    private suspend fun awaitNotice(vm: TicketViewModel) = withTimeout(10_000) {
        while (vm.state.value.notice == null) delay(10)
        vm.state.value.notice!!
    }

    @Test fun bothAgentsUseSharedArgumentsInTheCreatedWorktree() = runBlocking {
        val store = project("codex")
        val terminal = FakeTerminal()
        val vm = TicketViewModel("example", store, terminal)
        val collecting = launch(start = CoroutineStart.UNDISPATCHED) { vm.state.collect {} }
        try {
            for (agent in SessionAgent.entries) {
                // A missing executable must leave the repository untouched, including the default.
                terminal.available = false
                SwingUtilities.invokeAndWait { vm.request(if (agent == SessionAgent.CLAUDE) TicketAction.START_IN_CLAUDE else TicketAction.START_IN_CODEX) }
                val notice = awaitNotice(vm)
                assertTrue(notice.error)
                assertTrue(agent.label in notice.text)
                assertEquals("main", git("branch", "--format=%(refname:short)"))
            }
            terminal.available = true
            terminal.problem = "terminal failed"
            SwingUtilities.invokeAndWait { vm.request(TicketAction.START_IN_CODEX); vm.confirm() }
            withTimeout(10_000) { while (terminal.opened.isEmpty()) delay(10) }
            val notice = awaitNotice(vm)
            assertTrue(notice.error)
            assertTrue("Started in" in notice.text && "codex" in notice.text && "terminal failed" in notice.text, notice.text)
            assertEquals(listOf("codex") + SessionAgent.CODEX.arguments("example", null), terminal.opened.single().second)
            assertEquals(File(base, "work--example").toOkioPath(), terminal.opened.single().first)
            assertEquals(Status.IN_PROGRESS, store.snapshot.value.graph!!.ticket("example")!!.frontmatter!!.status)
        } finally { collecting.cancelAndJoin() }
    }

    @Test fun claudeAndPlainTerminalRemainAvailable() = runBlocking {
        val store = project("codex")
        val terminal = FakeTerminal()
        val vm = TicketViewModel("example", store, terminal)
        val collecting = launch(start = CoroutineStart.UNDISPATCHED) { vm.state.collect {} }
        try {
            SwingUtilities.invokeAndWait { vm.request(TicketAction.START_IN_CLAUDE); vm.confirm() }
            val notice = awaitNotice(vm)
            assertFalse(notice.error, notice.text)
            assertEquals(listOf("claude") + SessionAgent.CLAUDE.arguments("example", null), terminal.opened.single().second)
            SwingUtilities.invokeAndWait { vm.request(TicketAction.OPEN_TERMINAL) }
            withTimeout(10_000) { while (terminal.opened.size != 2) delay(10) }
            assertEquals(emptyList(), terminal.opened.last().second)
        } finally { collecting.cancelAndJoin() }
    }
}
