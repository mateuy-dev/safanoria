package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.parameters.options.option
import dev.mateuy.safanoria.core.Board
import dev.mateuy.safanoria.core.SystemFileSystem
import okio.Path.Companion.toPath

/** `safanoria board`: the markdown board, to stdout or a file. */
class BoardCommand : RepositoryCommand(name = "board") {
    override fun help(context: Context) =
        "Print a markdown board: a section per status, parents with their children and progress, " +
            "blocked tickets marked. No dates in it, so a committed board only changes when tickets do."

    private val output by option("--output", "-o", help = "Write to this file; links are relative to it (default: stdout, links relative to the root)")

    override fun run() {
        val repo = repository
        val file = output?.toPath()
        if (file == null) {
            echo(Board.markdown(repo.graph, repo.root), trailingNewline = false)
            return
        }
        val dir = file.parent ?: ".".toPath()
        if (!SystemFileSystem.exists(dir)) throw PrintMessage("No such directory: $dir", 2, true)
        val markdown = Board.markdown(repo.graph, SystemFileSystem.canonicalize(dir))
        SystemFileSystem.write(file) { writeUtf8(markdown) }
        echo("wrote ${displayPath(SystemFileSystem.canonicalize(file))}")
    }
}
