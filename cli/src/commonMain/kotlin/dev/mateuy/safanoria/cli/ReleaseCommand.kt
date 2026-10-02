package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import dev.mateuy.safanoria.core.GitException
import dev.mateuy.safanoria.core.Release
import dev.mateuy.safanoria.core.ReleaseRequest
import dev.mateuy.safanoria.core.ReleaseResult
import dev.mateuy.safanoria.core.Repository
import dev.mateuy.safanoria.core.Validator
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import okio.Path.Companion.toPath
import kotlin.time.Clock

/** `safanoria release <component> [<version>]`: SPEC §9 stamping. Doesn't commit. */
class ReleaseCommand : RepositoryCommand(name = "release") {
    override fun help(context: Context) =
        "Stamp resolvedIn.<component> on every done ticket with the component in its area and no " +
            "version for it yet, and log it. Run on the main branch, as part of the release commit."

    private val component by argument(help = "Component from safanoria.yaml")
    private val version by argument(help = "MAJOR.MINOR.PATCH (default: read from the component's version source; required for external ones)").optional()
    private val tickets by option("--ticket", help = "Stamp only this ticket (repeatable): when done doesn't mean 'in this release', e.g. external components").multiple()
    private val dryRun by option("--dry-run", help = "Show what would be stamped, write nothing").flag()
    private val anyBranch by option("--any-branch", help = "Run off the main branch").flag()
    private val date by option("--date", hidden = true, help = "Today's date (tests)")

    override fun run() {
        val repo = repository
        val today = date ?: Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        val ready = when (val r = Release.prepare(repo, ReleaseRequest(component, version, tickets.ifEmpty { null }), today)) {
            is ReleaseResult.Refused -> throw PrintMessage("Not stamped: ${r.reason}", 1, true)
            is ReleaseResult.Ready -> r
        }
        // A dry run writes nothing, so it may preview from any branch.
        if (!anyBranch) branchProblem(repo)?.let { if (dryRun) echo("warning: $it", err = true) else throw PrintMessage("Not stamped: $it", 1, true) }
        ready.warnings.forEach { echo("warning: $it", err = true) }
        val what = "${ready.component} ${ready.version}" +
            (if (ready.versionFromSource) " (from ${repo.config.components.getValue(ready.component).version!!.file})" else "")
        if (ready.stamped.isEmpty()) {
            echo("nothing to stamp with $what: no done ticket with ${ready.component} in its area is missing it")
            return
        }
        echo("${if (dryRun) "would stamp" else "stamped"} $what on ${ready.stamped.size} ticket${if (ready.stamped.size == 1) "" else "s"}:")
        ready.files.forEach { echo("  ${displayPath(it.path)}") }
        if (dryRun) return

        ready.files.forEach { f -> repo.fileSystem.write(f.path) { writeUtf8(f.text) } }
        val problems = Validator(Repository(repo.root, repo.fileSystem)).validate(ready.files.map { it.path })
        if (problems.isNotEmpty()) {
            problems.forEach { echo(it.copy(file = it.file?.let { p -> displayPath(p).toPath() }).toString(), err = true) }
            throw ProgramResult(1)
        }
    }

    /** §9: stamping reads mainBranch, so elsewhere it would stamp work that isn't released. */
    private fun branchProblem(repo: Repository): String? {
        val main = repo.config.mainBranch
        val current = try { repo.git.currentBranch() } catch (e: GitException) {
            return "can't tell the git branch (${e.message}); use --any-branch to stamp anyway"
        }
        if (current == main) return null
        val where = if (current == "HEAD") "a detached HEAD" else "branch '$current'"
        return "on $where, not '$main' (mainBranch, §9); use --any-branch to stamp anyway"
    }
}
