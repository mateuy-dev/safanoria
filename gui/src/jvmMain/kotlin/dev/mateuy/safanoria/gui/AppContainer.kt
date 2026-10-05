package dev.mateuy.safanoria.gui

import dev.mateuy.safanoria.core.Repository
import dev.mateuy.safanoria.gui.data.SystemTerminal
import dev.mateuy.safanoria.gui.data.Terminal
import dev.mateuy.safanoria.gui.data.TicketStore

/** What the app's ViewModels depend on, created once in `main` (manual dependency injection). */
class AppContainer(repository: Repository) {
    val ticketStore = TicketStore(repository.root)
    val terminal: Terminal = SystemTerminal()
}
