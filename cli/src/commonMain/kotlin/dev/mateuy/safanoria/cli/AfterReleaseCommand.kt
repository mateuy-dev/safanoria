package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import dev.mateuy.safanoria.core.Release
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * `safanoria-cli after-release`: every unchecked After Release task (SPEC §7.9) with its ticket,
 * `due` once its release is stamped, `waiting` before. Due tasks first, then in board order.
 */
class AfterReleaseCommand : AcrossBranchesCommand(name = "after-release") {
    override fun help(context: Context) =
        "List every unchecked After Release task with its ticket: due (its release is stamped) or waiting (not released yet)."

    private val due by option("--due", help = "Only tasks due now").flag()
    private val format by option("--format", help = "Output format").choice("text", "json").default("text")

    override fun run() {
        val tasks = graph.tickets.flatMap { t -> Release.afterReleaseTasks(t).map { t.fileId to it } }
            .filter { !due || it.second.due }
            .sortedBy { !it.second.due } // stable: board order within each group
        if (format == "json") {
            echo(
                buildJsonObject {
                    putJsonArray("tasks") {
                        tasks.forEach { (id, task) ->
                            add(buildJsonObject { put("ticket", id); put("text", task.text); put("due", task.due) })
                        }
                    }
                }.toString(),
            )
            return
        }
        if (tasks.isEmpty()) {
            echo(if (due) "No After Release tasks are due." else "No unchecked After Release tasks.")
            return
        }
        val width = tasks.maxOf { it.first.length }
        tasks.forEach { (id, task) ->
            val state = if (task.due) "due    " else "waiting"
            val indent = " ".repeat(state.length + width + 4) // continuation lines go under the text column
            echo("$state  ${id.padEnd(width)}  " + task.text.lines().joinToString("\n$indent") { it.trimStart() })
        }
    }
}
