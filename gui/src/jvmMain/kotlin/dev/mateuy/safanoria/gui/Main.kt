package dev.mateuy.safanoria.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.mateuy.safanoria.core.Repository
import dev.mateuy.safanoria.core.SPEC_VERSION
import dev.mateuy.safanoria.gui.theme.SafanoriaTheme
import dev.mateuy.safanoria.gui.theme.rememberAppIcon
import okio.Path.Companion.toPath
import java.awt.GraphicsEnvironment
import java.io.File
import kotlin.system.exitProcess

/** What a `safanoria` command line asks for. */
sealed interface Launch {
    /** Open the project containing [path] (null: the working directory). */
    data class Open(val path: String?) : Launch
    /** Print [text] and exit with [status]. */
    data class Message(val text: String, val status: Int) : Launch
}

private const val USAGE = "Usage: safanoria [path]\n\n" +
    "Opens the desktop app on the project containing path (default: the working directory).\n" +
    "The window outlives the terminal; SAFANORIA_FOREGROUND=1 keeps the app attached, to see its output.\n" +
    "The command-line tool is safanoria-cli (safanoria-cli --help)."

/**
 * `safanoria` was the command-line tool up to 0.2: anything that isn't a directory to open is
 * most likely a command meant for it (a git hook or a script not updated yet), so the answer
 * names `safanoria-cli` with the same arguments instead of opening a window.
 */
fun launch(args: List<String>, version: String, isDirectory: (String) -> Boolean): Launch {
    val first = args.firstOrNull() ?: return Launch.Open(null)
    return when {
        first == "--help" || first == "-h" -> Launch.Message(USAGE, 0)
        first == "--version" -> Launch.Message("safanoria $version (spec $SPEC_VERSION)", 0)
        args.size == 1 && isDirectory(first) -> Launch.Open(first)
        else -> Launch.Message(
            "safanoria opens the desktop app; the command-line tool is now safanoria-cli. Run:\n" +
                "  safanoria-cli ${args.joinToString(" ")}\n" +
                "`safanoria-cli update` and `safanoria-cli hook install` update a project's hooks to the new name.",
            2,
        )
    }
}

/** Says that there is no project to open, until it is closed. */
private fun noProjectWindow(message: String) = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Safanoria",
        state = rememberWindowState(size = DpSize(640.dp, 220.dp), position = WindowPosition(Alignment.Center)),
        icon = rememberAppIcon(),
    ) {
        SafanoriaTheme {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SelectionContainer { Text(message) }
                    Button(onClick = ::exitApplication) { Text("Close") }
                }
            }
        }
    }
}

/** `safanoria [path]`: opens the project containing [path] (default: the working directory). */
fun main(args: Array<String>) {
    val version = System.getProperty("safanoria.version") ?: "dev"
    val start = when (val launch = launch(args.toList(), version) { File(it).isDirectory }) {
        is Launch.Open -> launch.path?.toPath()
        is Launch.Message -> {
            (if (launch.status == 0) System.out else System.err).println(launch.text)
            exitProcess(launch.status)
        }
    }
    val repository = runCatching { Repository.find(start) }.getOrNull() ?: run {
        val message = "No safanoria.yaml found in ${File(start?.toString() ?: "").absolutePath} or its parents.\n" +
            "`safanoria-cli init` sets up a project."
        System.err.println(message)
        // The launcher has detached the app from the terminal by now: only a window shows this.
        if (!GraphicsEnvironment.isHeadless()) noProjectWindow(message)
        exitProcess(2)
    }
    val container = AppContainer(repository)
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Safanoria · ${repository.root.name}",
            state = rememberWindowState(size = DpSize(1400.dp, 900.dp)),
            icon = rememberAppIcon(),
        ) {
            App(container)
        }
    }
}
