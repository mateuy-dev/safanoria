package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.terminal
import dev.mateuy.safanoria.tui.runTui

/** `safanoria-cli tui`: the terminal app, the desktop app's screens with the keyboard and the mouse. */
class TuiCommand : RepositoryCommand(name = "tui") {
    override fun help(context: Context) =
        "Open the terminal app: the board, tickets and versions, with their actions (start, finish, merge…), " +
            "by keyboard and mouse. What `safanoria-cli` alone opens on a terminal."

    override fun run() {
        // Prompts exist only when stdin and stdout are a terminal: what the app needs too.
        if (prompts == null) throw PrintMessage("The terminal app needs a terminal: stdin and stdout must be one.", 1, true)
        runTui(currentContext.terminal, repository.root)
    }
}
