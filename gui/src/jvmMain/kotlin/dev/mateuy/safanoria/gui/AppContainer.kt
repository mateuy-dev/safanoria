package dev.mateuy.safanoria.gui

import dev.mateuy.safanoria.core.Repository
import dev.mateuy.safanoria.gui.data.SystemTerminal
import dev.mateuy.safanoria.gui.data.Terminal
import dev.mateuy.safanoria.gui.data.TicketStore
import dev.mateuy.safanoria.gui.theme.IconFile
import dev.mateuy.safanoria.gui.theme.readProjectIcon

/** What the app's ViewModels depend on, created once in `main` (manual dependency injection). */
class AppContainer(repository: Repository) {
    val ticketStore = TicketStore(repository.root)
    val terminal: Terminal = SystemTerminal()
    /** Read once: the icon of a window doesn't follow the file. */
    val projectIcon: IconFile? = readProjectIcon(repository.root.toFile(), repository.config.icon)
}
