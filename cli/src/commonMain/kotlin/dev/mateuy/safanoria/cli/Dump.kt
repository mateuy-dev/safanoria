package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import dev.mateuy.safanoria.core.Resolution
import dev.mateuy.safanoria.core.SystemFileSystem
import dev.mateuy.safanoria.core.Ticket
import dev.mateuy.safanoria.core.text
import okio.Path.Companion.toPath

/** Prints what the parser sees in a ticket. For debugging the parser, and a smoke test. */
class Dump : CliktCommand(name = "dump") {
    override val hiddenFromHelp: Boolean = true
    override fun help(context: Context) = "Show how a ticket file is parsed (debugging)."

    private val file by argument(help = "Ticket file")

    override fun run() {
        val path = file.toPath()
        if (!SystemFileSystem.exists(path)) throw com.github.ajalt.clikt.core.PrintMessage("No such file: $file", 2, true)
        val ticket = Ticket(path, SystemFileSystem.read(path) { readUtf8() })
        val f = ticket.frontmatter
        if (f != null) {
            echo("frontmatter:")
            f.keys.forEach { echo("  ${it.value} @${it.line}") }
            echo("  → id=${f.id?.value} type=${f.type?.text} status=${f.status?.text} parent=${f.parent?.value} " +
                "blockedBy=${f.blockedBy.map { it.value }} resolvedIn=${f.resolvedIn?.mapValues { it.value.value }}")
        }
        val body = ticket.body
        echo("sections:")
        for (s in body.sections) {
            val items = body.checklist(s.name)
            val summary = if (items.isEmpty()) "" else
                "  ${items.count { it.checked }}/${items.size} checked" +
                    items.mapNotNull { it.childId }.let { if (it.isEmpty()) "" else ", children: ${it.joinToString()}" }
            echo("  ${s.name} @${s.headingLine}..${s.lastLine}$summary")
        }
        if (body.learnings.isNotEmpty()) {
            echo("learnings: ${body.learnings.size}, pending: ${body.learnings.count { it.resolution == Resolution.Pending }}")
        }
        if (body.workLog.isNotEmpty()) {
            val last = body.workLog.last()
            echo("work log: ${body.workLog.size} entries, last: ${last.date} · ${last.ref} @${last.line}")
        }
        if (body.quotes.isNotEmpty()) echo("quotes: ${body.quotes.size}")
        val diagnostics = ticket.parseDiagnostics + (f?.schemaDiagnostics() ?: emptyList())
        if (diagnostics.isEmpty()) {
            echo("ok")
        } else {
            diagnostics.forEach { echo(it.toString(), err = true) }
            throw ProgramResult(1)
        }
    }
}
