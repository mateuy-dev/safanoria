package dev.mateuy.safanoria.tui

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import com.github.ajalt.mordant.input.isCtrlC
import com.github.ajalt.mordant.terminal.Terminal
import dev.mateuy.safanoria.views.TicketStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okio.Path
import kotlin.time.Duration.Companion.milliseconds

/** A screen of the app: it draws itself and takes the keys. The mouse reaches it through the regions it draws. */
internal interface Page {
    fun draw(p: Painter)

    /** Handles [e], or returns false for the app to: back, quit, help. */
    fun key(e: KeyboardEvent): Boolean

    /** True while the page takes typed text (a reason to reopen): then every key is the page's. */
    val typing: Boolean get() = false
}

/** What a page can ask of the app. */
internal interface Navigator {
    val store: TicketStore
    val terminal: Terminal
    val workplace: WorkplaceLauncher get() = WorkplaceLauncher()
    fun open(page: Page)
    fun back()

    /** Runs [block] in the background; the screen is drawn again when the tickets change. */
    fun launch(block: suspend () -> Unit)

    /** Leaves the screen and the keys to [block] (a shell), then comes back. */
    suspend fun outside(block: () -> Unit)

    /** Opens the ticket screen of [id]. */
    fun openTicket(id: String)
    fun openVersions()
}

/**
 * Runs the terminal app on the project at [root] until the user quits. [terminal] must be
 * interactive, input and output.
 */
fun runTui(terminal: Terminal, root: Path) = Tui(terminal, TicketStore(root)).run()

/**
 * The terminal app: pages on a stack, the board at the bottom, drawn on the alternate screen and
 * driven by the keyboard and the mouse. Tickets are read (and changed) through [store], as the
 * desktop app does.
 */
internal class Tui(override val terminal: Terminal, override val store: TicketStore) : Navigator {
    private val pages = mutableListOf<Page>()
    private val display = Display(terminal)
    private val events = Channel<Any>(Channel.UNLIMITED)
    private var frame: Canvas? = null
    private var help = false
    private var running = true
    private lateinit var scope: CoroutineScope

    // Raw mode, with the mouse; null while a shell has the terminal.
    private var raw: Input? = null
    private val rawLock = Mutex()

    private object Redraw

    /** Runs until the user quits. The terminal must be interactive. */
    fun run(): Unit = runBlocking {
        scope = this
        pages += BoardPage(this@Tui)
        raw = openInput(terminal)
        display.enter()
        launch(Dispatchers.IO) {
            while (isActive) {
                val event = rawLock.withLock { raw?.read() }
                when {
                    event != null -> events.send(event)
                    raw == null -> delay(50)
                }
            }
        }
        launch { store.snapshot.collect { events.send(Redraw) } }
        launch { store.refresh() }
        try {
            while (running) {
                draw()
                // Also wakes up now and then: the terminal may have been resized.
                when (val event = withTimeoutOrNull(300.milliseconds) { events.receive() }) {
                    is KeyboardEvent -> key(event)
                    is MouseEvent -> mouse(event)
                }
            }
        } finally {
            rawLock.withLock { raw?.close(); raw = null }
            display.leave()
            coroutineContext.cancelChildren()
        }
    }

    private fun draw() {
        val size = terminal.updateSize()
        val canvas = Canvas(size.width, size.height)
        val p = canvas.painter()
        pages.last().draw(p)
        if (help) drawHelp(p)
        display.show(canvas)
        frame = canvas
    }

    private fun key(e: KeyboardEvent) {
        if (e.isCtrlC) { running = false; return }
        if (help) { help = false; return }
        val page = pages.last()
        if (page.key(e)) return
        if (page.typing) return
        when (e.key) {
            "?" -> help = true
            "r" -> launch { store.refresh() }
            "q" -> if (pages.size > 1) back() else running = false
            "Escape", "Backspace" -> back()
        }
    }

    private fun mouse(e: MouseEvent) {
        val canvas = frame ?: return
        when {
            e.wheelUp || e.wheelDown -> canvas.hit(e.x, e.y) { it.scroll }?.invoke(if (e.wheelDown) 1 else -1)
            e.left -> if (help) help = false else canvas.hit(e.x, e.y) { it.click }?.invoke()
        }
    }

    override fun open(page: Page) {
        pages += page
    }

    override fun back() {
        if (pages.size > 1) pages.removeAt(pages.lastIndex)
    }

    override fun openTicket(id: String) = open(TicketPage(this, id))

    override fun openVersions() = open(VersionsPage(this))

    override fun launch(block: suspend () -> Unit) {
        // A failure must not end the app: the store and the pages report theirs.
        scope.launch { runCatching { block() }; events.send(Redraw) }
    }

    override suspend fun outside(block: () -> Unit) {
        rawLock.withLock {
            raw?.close()
            raw = null
            display.leave()
            try {
                block()
            } finally {
                raw = openInput(terminal)
                display.enter()
            }
        }
    }

    private fun drawHelp(p: Painter) {
        val lines = HELP.lines()
        val w = minOf(p.width - 4, lines.maxOf { textWidth(it) } + 4)
        val h = minOf(p.height - 2, lines.size + 2)
        val x = (p.width - w) / 2
        val y = (p.height - h) / 2
        p.fill(x, y, w, h)
        p.box(x, y, w, h, Theme.accent)
        p.text(x + 2, y, " Keys ", Theme.accent)
        lines.take(h - 2).forEachIndexed { i, line -> p.text(x + 2, y + 1 + i, line, max = w - 4) }
        p.region(0, 0, p.width, p.height, click = { help = false })
    }

    private companion object {
        val HELP = """
            Everywhere
              ?                 these keys
              Esc, Backspace, q back (q on the board: quit)
              Ctrl-C            quit
              r                 read the tickets again
              Mouse             click cards, buttons, chips, tabs; wheel scrolls

            Board
              ← → ↑ ↓, h l k j  move between columns and cards
              Enter             open the ticket (or expand a collapsed column)
              Space             collapse or expand the column
              Tab               the filters: ← → to move, Space to toggle
              1-4               filter by type
              b, c, x           blocked only, show children, clear filters
              v                 versions
              F                 fetch the remotes

            Ticket
              ↑ ↓, PgUp PgDn    scroll
              Tab, Enter        go through the links and buttons, open one
              s c x             start a shell, start Claude Code, start Codex
              w o d f m i       create worktree, open a shell, diff, finish,
                                merge, back to in progress
              p                 the parent ticket

            Versions
              ↑ ↓, Enter        choose a ticket, open it
              ← →, Tab          the component
        """.trimIndent()
    }
}
