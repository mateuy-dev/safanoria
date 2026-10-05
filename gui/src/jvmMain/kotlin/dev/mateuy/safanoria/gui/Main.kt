package dev.mateuy.safanoria.gui

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.mateuy.safanoria.core.Repository
import okio.Path.Companion.toPath
import kotlin.system.exitProcess

/** `safanoria-gui [path]`: opens the project containing [path] (default: the working directory). */
fun main(args: Array<String>) {
    val start = args.firstOrNull()?.toPath()
    val repository = runCatching { Repository.find(start) }.getOrNull() ?: run {
        System.err.println("No safanoria.yaml found in ${start ?: "the working directory"} or its parents.")
        exitProcess(2)
    }
    val container = AppContainer(repository)
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Safanoria · ${repository.root.name}",
            state = rememberWindowState(size = DpSize(1400.dp, 900.dp)),
        ) {
            App(container)
        }
    }
}
