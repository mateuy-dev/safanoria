package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import dev.mateuy.safanoria.core.Embedded
import dev.mateuy.safanoria.core.SPEC_VERSION

class Safanoria : CliktCommand(name = "safanoria") {
    override fun help(context: Context) = "Tickets as markdown files in your repository (SPEC.md)."
    override fun run() = Unit
}

class Version : CliktCommand(name = "version") {
    override fun help(context: Context) = "Print the tool version and the spec version it implements."
    override fun run() = echo("safanoria ${Embedded.VERSION} (spec $SPEC_VERSION)")
}

fun main(args: Array<String>) = Safanoria().subcommands(Version()).main(args)
