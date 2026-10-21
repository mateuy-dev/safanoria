package dev.mateuy.safanoria.tui

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import dev.mateuy.safanoria.views.TicketStore
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The pages drawn from tickets on disk (no git: the files as they are), and what keys and clicks do. */
class PagesTest {
    private val root: File = Files.createTempDirectory("safanoria-tui").toFile()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun ticket(id: String, status: String = "backlog", extra: String = "", body: String = "Why $id.") {
        File(root, "tickets/$id.md").writeText(
            listOf("---", "id: $id", "type: feature", "title: Title of $id", "status: $status", "priority: medium", "size: S") +
                listOf("created: 2026-10-01", "updated: 2026-10-02") + listOfNotNull(extra.ifEmpty { null }) +
                listOf("---", "", "## Objective", "", body, "", "## Work Log", "").joinToString("\n").lines(),
        )
    }

    private fun File.writeText(lines: List<String>) = writeText(lines.joinToString("\n"))

    private fun project(): Nav {
        File(root, "tickets").mkdirs()
        File(root, "safanoria.yaml").writeText("safanoria: 1\ndir: tickets\nmainBranch: main\nworktree: ../project--{id}\n")
        return Nav(TicketStore(root.toOkioPath()))
    }

    /** Records what the pages ask for, instead of a stack of pages. */
    private class Nav(override val store: TicketStore) : Navigator {
        override val terminal = Terminal(terminalInterface = TerminalRecorder(AnsiLevel.NONE, width = 100, height = 30))
        val opened = mutableListOf<String>()
        override fun open(page: Page) { opened += page::class.simpleName!! }
        override fun back() { opened += "back" }
        override fun launch(block: suspend () -> Unit) = runBlocking { block() }
        override suspend fun outside(block: () -> Unit) = block()
        override fun openTicket(id: String) { opened += id }
        override fun openVersions() { opened += "versions" }
    }

    private fun Page.render(width: Int = 100, height: Int = 30): Canvas = Canvas(width, height).also { draw(it.painter()) }

    private fun Canvas.text() = rows.joinToString("\n") { row -> row.joinToString("") { it.text }.trimEnd() }

    private fun Canvas.click(text: String, nav: Nav) {
        val y = rows.indexOfFirst { row -> text in row.joinToString("") { it.text } }
        val x = rows[y].joinToString("") { it.text }.indexOf(text)
        hit(x, y) { it.click }!!.invoke()
    }

    @Test
    fun theBoardShowsColumnsAndCards() {
        val nav = project()
        ticket("first-one")
        ticket("second", status = "in-progress")
        ticket("old", status = "done")
        runBlocking { nav.store.refresh() }
        val text = BoardPage(nav).render().text()
        assertTrue("Board · 3 tickets" in text, text)
        assertTrue("● Backlog 1" in text && "● In progress 1" in text, text)
        assertTrue("first-one" in text && "Title of second" in text, text)
        // Released is collapsed: its count, not its cards.
        assertTrue("old" !in text, text)
    }

    @Test
    fun keysMoveTheSelectionAndEnterOpensIt() {
        val nav = project()
        ticket("first-one")
        ticket("second", status = "in-progress")
        runBlocking { nav.store.refresh() }
        val board = BoardPage(nav)
        board.render()
        board.key(KeyboardEvent("Enter"))
        board.key(KeyboardEvent("ArrowRight"))
        board.key(KeyboardEvent("ArrowRight"))
        board.render()
        board.key(KeyboardEvent("Enter"))
        assertEquals(listOf("first-one", "second"), nav.opened)
    }

    @Test
    fun clickingACardOpensItAndAChipFilters() {
        val nav = project()
        ticket("first-one")
        ticket("a-bug", extra = "")
        File(root, "tickets/a-bug.md").let { it.writeText(it.readText().replace("type: feature", "type: bug")) }
        runBlocking { nav.store.refresh() }
        val board = BoardPage(nav)
        board.render().click("first-one", nav)
        assertEquals(listOf("first-one"), nav.opened)
        board.render().click("bug", nav)
        val text = board.render().text()
        assertTrue("1 of 2 tickets" in text && "first-one" !in text, text)
    }

    @Test
    fun theTicketPageShowsFactsLinksBodyAndActions() {
        val nav = project()
        ticket("parent", status = "in-progress", body = "A **parent** ticket.")
        ticket("child", extra = "parent: parent")
        ticket("other", status = "review", extra = "blockedBy: [parent]")
        runBlocking { nav.store.refresh() }
        val page = TicketPage(nav, "parent")
        val text = page.render().text()
        assertTrue("parent Title of parent" in text && "In progress" in text, text)
        assertTrue("[Open shell (o)] [Finish (f)]" in text, text)
        assertTrue("type feature · priority medium · size S" in text, text)
        assertTrue("Blocks      other" in text, text)
        assertTrue("Children (1)" in text && "child" in text, text)
        assertTrue("A parent ticket." in text, text)

        page.key(KeyboardEvent("f"))
        val confirm = page.render().text()
        assertTrue("Set parent to review?" in confirm, confirm)
        page.key(KeyboardEvent("Escape"))
        assertTrue("Set parent to review?" !in page.render().text())
    }

    @Test
    fun tabGoesThroughLinksAndEnterOpensThem() {
        val nav = project()
        ticket("parent", status = "in-progress")
        ticket("child", extra = "parent: parent")
        runBlocking { nav.store.refresh() }
        val page = TicketPage(nav, "child")
        page.render()
        // Start, Start in Claude, then the parent.
        repeat(3) { page.key(KeyboardEvent("Tab")) }
        page.render()
        page.key(KeyboardEvent("Enter"))
        page.key(KeyboardEvent("p"))
        assertEquals(listOf("parent", "parent"), nav.opened)
    }

    @Test
    fun reopeningAsksForTheReason() {
        val nav = project()
        ticket("done-soon", status = "review")
        runBlocking { nav.store.refresh() }
        val page = TicketPage(nav, "done-soon")
        page.render()
        page.key(KeyboardEvent("i"))
        assertTrue(page.typing)
        "No tests".forEach { page.key(KeyboardEvent(it.toString())) }
        val text = page.render().text()
        assertTrue("> No tests" in text, text)
    }
}
