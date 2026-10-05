package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import dev.mateuy.safanoria.core.Branches
import dev.mateuy.safanoria.core.CONFIG_FILE
import dev.mateuy.safanoria.core.Diagnostic
import dev.mateuy.safanoria.core.Severity
import dev.mateuy.safanoria.core.SystemFileSystem
import dev.mateuy.safanoria.core.Validator
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okio.Path
import okio.Path.Companion.toPath

/** `safanoria-cli validate`: SPEC §12 checks. Exit 0 valid, 1 problems, 2 usage or no repository. */
class Validate : RepositoryCommand(name = "validate") {
    override fun help(context: Context) =
        "Check tickets and safanoria.yaml against SPEC.md §12. With files (or --staged), report only " +
            "problems in them or caused by them; every ticket is still read for cross-ticket rules."

    private val files by argument(help = "Ticket files or safanoria.yaml (default: everything)").multiple()
    private val staged by option("--staged", help = "Check the git-staged tickets and safanoria.yaml (for pre-commit hooks)").flag()
    private val format by option("--format", help = "Output format").choice("text", "json").default("text")
    private val checkout by option("--checkout", help = "Don't read other branches: references must name tickets in this checkout").flag()

    override fun run() {
        if (staged && files.isNotEmpty()) throw PrintMessage("Give files or --staged, not both.", 2, true)
        val repo = repository
        val only: List<Path>? = when {
            staged -> stagedPaths().also {
                if (it.isEmpty()) {
                    if (format == "json") report(emptyList(), checked = plural(0, "file")) else echo("ok: no staged tickets")
                    return
                }
            }
            files.isNotEmpty() -> files.map { f ->
                val path = f.toPath()
                if (!SystemFileSystem.exists(path)) throw PrintMessage("No such file: $f", 2, true)
                path
            }
            else -> null
        }
        // Other branches, remote-tracking ones too (CI often has only origin/*), only add known ids
        // and the id-created-twice warning (SPEC §12, §14); the files checked are this checkout's.
        val branches = if (checkout) null else Branches.read(repo, remote = true)
        val diagnostics = Validator(repo, branches).validate(only)
        val checked = only?.let { plural(it.size, "file") } ?: plural(repo.tickets.size, "ticket")
        report(diagnostics, checked)
    }

    private fun plural(n: Int, noun: String) = "$n $noun${if (n == 1) "" else "s"}"

    /** Staged files that validate cares about: tickets and the config. */
    private fun stagedPaths(): List<Path> {
        val repo = repository
        val dir = repo.config.dir.trimEnd('/') + "/"
        return repo.git.stagedFiles()
            .filter { it == CONFIG_FILE || (it.startsWith(dir) && it.endsWith(".md")) }
            .map { repo.root / it }
            .filter { SystemFileSystem.exists(it) } // deleted files can't be checked
    }

    private fun report(diagnostics: List<Diagnostic>, checked: String) {
        fun shown(path: Path?): String? = path?.let(::displayPath)
        val errors = diagnostics.count { it.severity == Severity.ERROR }
        when (format) {
            "json" -> echo(buildJsonObject {
                put("valid", errors == 0)
                put("checked", checked)
                putJsonArray("diagnostics") {
                    for (d in diagnostics) addJsonObject {
                        put("file", shown(d.file)?.let(::JsonPrimitive) ?: JsonNull)
                        put("line", d.line?.let(::JsonPrimitive) ?: JsonNull)
                        put("column", d.column?.let(::JsonPrimitive) ?: JsonNull)
                        put("severity", d.severity.name.lowercase())
                        put("code", d.code)
                        put("message", d.message)
                    }
                }
            }.toString())
            else -> {
                diagnostics.forEach { echo(it.copy(file = shown(it.file)?.toPath()).toString()) }
                val files = diagnostics.mapNotNull { it.file }.distinct().size
                echo(
                    if (diagnostics.isEmpty()) "ok: $checked valid"
                    else "${plural(diagnostics.size, "problem")} in ${plural(files, "file")} ($checked checked)",
                    err = diagnostics.isNotEmpty(),
                )
            }
        }
        if (errors > 0) throw ProgramResult(1)
    }
}
