package dev.mateuy.safanoria.tui

import com.github.ajalt.mordant.input.KeyboardEvent
import dev.mateuy.safanoria.core.TicketType
import dev.mateuy.safanoria.core.text
import dev.mateuy.safanoria.views.VersionTicket
import dev.mateuy.safanoria.views.VersionsViewState
import dev.mateuy.safanoria.views.versionsViewState

/** The versions of a component, each with the tickets it shipped, what is to release first; as in the desktop app. */
internal class VersionsPage(private val nav: Navigator) : Page {
    private var component: String? = null
    private var state = VersionsViewState()

    /** The ticket the keyboard is on, by its place among the ticket rows. */
    private var selected = 0
    private var scroll = 0
    private var follow = true
    private var pageSize = 10

    /** A line of the list: a version's heading, or a ticket ([id]) and how deep it is nested. */
    private class Row(val runs: List<Run>, val id: String? = null)

    override fun draw(p: Painter) {
        state = versionsViewState(nav.store.snapshot.value, component)
        var x = p.button(1, 0, "← Back") { nav.back() } + 1
        x = p.runs(x, 0, listOf(Run("Versions", Theme.accent), Run(state.component?.let { " · $it" } ?: ""))) + 2
        if (state.loading) p.text(x, 0, "reading…", Theme.dim)

        var y = 1
        if (state.components.size > 1) {
            var tx = 1
            for (name in state.components) {
                val style = if (name == state.component) Theme.accent + Theme.selected else Plain
                val end = p.text(tx, y, " $name ", style)
                p.region(tx, y, end - tx, 1, click = { selectComponent(name) })
                tx = end + 1
            }
            y++
        }
        p.rule(y++)
        state.error?.let { p.text(1, y++, it, Theme.error, max = p.width - 2) }

        val rows = rows()
        val tickets = rows.indices.filter { rows[it].id != null }
        selected = selected.coerceIn(0, (tickets.size - 1).coerceAtLeast(0))
        val area = p.area(1, y, p.width - 2, p.height - y - 1)
        pageSize = area.height
        val selectedRow = tickets.getOrNull(selected)
        if (follow && selectedRow != null) {
            // The version's heading too, when it fits.
            val top = if (selectedRow > 0 && rows[selectedRow - 1].id == null) selectedRow - 1 else selectedRow
            if (top < scroll) scroll = top
            if (selectedRow >= scroll + area.height) scroll = selectedRow - area.height + 1
        }
        follow = false
        val max = (rows.size - area.height).coerceAtLeast(0)
        scroll = scroll.coerceIn(0, max)
        area.region(0, 0, area.width, area.height, scroll = { dy -> scroll = (scroll + dy * 3).coerceIn(0, max) })
        val list = area.scrolled(scroll)
        rows.forEachIndexed { i, row ->
            if (i < scroll || i >= scroll + area.height) return@forEachIndexed
            val isSelected = i == selectedRow
            if (isSelected) list.fill(0, i, area.width, 1, Theme.selected)
            list.runs(0, i, if (isSelected) row.runs.map { Run(it.text, it.style + Theme.selected) } else row.runs, max = area.width)
            row.id?.let { id -> list.region(0, i, area.width, 1, click = { selected = tickets.indexOf(i); nav.openTicket(id) }) }
        }
        when {
            state.loading && rows.isEmpty() -> area.text(0, 0, "Reading tickets…", Theme.dim)
            state.component == null -> area.text(0, 0, "No components in safanoria.yaml: nothing gets a version.", Theme.dim)
            rows.isEmpty() -> area.text(0, 0, "No tickets released or to release in ${state.component}.", Theme.dim)
        }
        p.statusBar("↑↓ choose · ⏎ open" + (if (state.components.size > 1) " · ←→ component" else "") + " · esc back · r refresh · ? keys")
    }

    private fun rows(): List<Row> = buildList {
        for (release in state.releases) {
            if (isNotEmpty()) add(Row(emptyList()))
            val heading = release.version ?: "To release"
            add(Row(listOf(Run(heading, Theme.bold), Run(" · ${release.count} ticket${if (release.count == 1) "" else "s"}", Theme.dim))))
            fun ticket(t: VersionTicket, depth: Int) {
                add(
                    Row(
                        listOfNotNull(
                            Run(if (depth == 0) "  " else "    └ "),
                            Run(t.id, Theme.dim),
                            Run("  " + t.title),
                            t.type?.takeIf { it != TicketType.FEATURE }?.let { Run("  " + it.text, Theme.dim) },
                            t.parentId?.let { Run("  ↑ $it", Theme.dim) },
                        ),
                        t.id,
                    ),
                )
                t.children.forEach { ticket(it, depth + 1) }
            }
            release.tickets.forEach { ticket(it, 0) }
        }
    }

    private fun selectComponent(name: String) {
        component = name
        selected = 0
        scroll = 0
        follow = true
    }

    override fun key(e: KeyboardEvent): Boolean {
        val components = state.components
        val at = components.indexOf(state.component)
        when (e.key) {
            "ArrowUp", "k" -> selected--
            "ArrowDown", "j" -> selected++
            "PageUp" -> selected -= pageSize - 1
            "PageDown", " " -> selected += pageSize - 1
            "Home", "g" -> selected = 0
            "End", "G" -> selected = Int.MAX_VALUE
            "Enter" -> rows().mapNotNull { it.id }.getOrNull(selected)?.let { nav.openTicket(it) }
            "ArrowLeft", "h" -> if (at > 0) selectComponent(components[at - 1]) else nav.back()
            "ArrowRight", "l", "Tab" -> if (components.size > 1) selectComponent(components[(at + 1) % components.size])
            else -> return false
        }
        follow = true
        return true
    }
}
