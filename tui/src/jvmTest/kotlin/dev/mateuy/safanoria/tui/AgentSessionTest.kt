package dev.mateuy.safanoria.tui

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
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import dev.mateuy.safanoria.core.HerdrException
import dev.mateuy.safanoria.core.Size

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
    private class Launcher(var place: Workplace = Workplace.HERE) : WorkplaceLauncher() {
        var available = true
        var exit: Int? = 0
        var fails = false
        var command = emptyList<String>()
        var directory: Path? = null
        var agent: SessionAgent? = null
        var size: Size? = null
        override fun has(program: String) = available
        override fun current() = place
        override fun here(dir: Path, command: List<String>): Int? {
            directory = dir; this.command = command; return exit
        }
        override fun quiet(command: List<String>): Boolean { this.command = command; return !fails }
        override fun herdr(dir: Path, id: String, agent: SessionAgent?, size: Size?): String {
            directory = dir; this.agent = agent; this.size = size
            if (fails) throw HerdrException("opened pane but ${agent?.label} failed")
            return "opened ${agent?.label}"
        }
    }
    private class Nav(override val store: TicketStore, override val workplace: Launcher) : Navigator {
        override val terminal = Terminal(terminalInterface = TerminalRecorder(AnsiLevel.NONE, width = 110, height = 35))
        var returned = false
        override fun open(page: Page) {}
        override fun back() {}
        override fun launch(block: suspend () -> Unit) = runBlocking { block() }
        override suspend fun outside(block: () -> Unit) { try { block() } finally { returned = true } }
        override fun openTicket(id: String) {}
        override fun openVersions() {}
    }
    private fun TicketPage.render(): String = Canvas(110, 35).also { draw(it.painter()) }.rows.joinToString("\n") { row -> row.joinToString("") { it.text } }

    @Test fun defaultAndMissingAgentsAreShownBeforeStarting() = runBlocking {
        val store = project("codex")
        val launcher = Launcher().apply { available = false }
        val page = TicketPage(Nav(store, launcher), "example")
        val text = page.render()
        assertTrue(text.indexOf("Start in Codex (default)") < text.indexOf("Start in Claude Code"), text)
        for ((key, agent) in listOf("c" to SessionAgent.CLAUDE, "x" to SessionAgent.CODEX)) {
            page.key(KeyboardEvent(key))
            assertTrue("${agent.label} isn't installed" in page.render())
            assertEquals("main", git("branch", "--format=%(refname:short)"))
        }
    }

    @Test fun localAgentLaunchRestoresThePageAndReportsFailureAfterStart() = runBlocking {
        val store = project()
        val launcher = Launcher().apply { exit = 23 }
        val nav = Nav(store, launcher)
        val page = TicketPage(nav, "example")
        page.render()
        page.key(KeyboardEvent("x"))
        assertTrue("Start example in Codex?" in page.render())
        page.key(KeyboardEvent("Enter"))
        assertEquals(listOf("codex") + SessionAgent.CODEX.arguments("example", null), launcher.command)
        assertEquals(File(base, "work--example").toOkioPath(), launcher.directory)
        assertTrue(nav.returned)
        val text = page.render()
        assertTrue("Started in" in text && "Codex exited with status 23" in text, text)
        assertEquals(Status.IN_PROGRESS, store.snapshot.value.graph!!.ticket("example")!!.frontmatter!!.status)
    }

    @Test fun everyAgentAndPlainShellUseEachWorkplace() = runBlocking {
        val store = project()
        val launcher = Launcher()
        val nav = Nav(store, launcher)
        val dir = root.toOkioPath()
        for (place in Workplace.entries) for (agent in listOf(null) + SessionAgent.entries) {
            launcher.place = place
            nav.work(dir, "example", agent, Size.L)
            when (place) {
                Workplace.HERDR -> { assertEquals(agent, launcher.agent); assertEquals(dir, launcher.directory); assertEquals(Size.L, launcher.size) }
                Workplace.TMUX -> assertEquals(listOf("tmux", "new-window", "-c", dir.toString(), "-n", "example") + (agent?.let { listOf((listOf(it.option) + it.arguments("example", null)).joinToString(" ", transform = ::shellQuoted)) } ?: emptyList()), launcher.command)
                Workplace.HERE -> { assertEquals(agent?.let { listOf(it.option) + it.arguments("example", null) } ?: listOf(userShell()), launcher.command); assertTrue(nav.returned) }
            }
        }
        launcher.fails = true
        for (place in listOf(Workplace.HERDR, Workplace.TMUX)) {
            launcher.place = place
            val notice = nav.work(dir, "example", SessionAgent.CODEX, Size.L, "Started in $dir")!!
            assertTrue(notice.error)
            assertTrue("Started in $dir" in notice.text && "Codex" in notice.text, notice.text)
        }
        launcher.place = Workplace.HERE
        launcher.exit = null
        assertTrue(nav.work(dir, "example", SessionAgent.CLAUDE, Size.S)!!.error)
    }
}
