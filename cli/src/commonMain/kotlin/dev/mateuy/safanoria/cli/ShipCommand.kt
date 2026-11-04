package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import dev.mateuy.safanoria.core.Branches
import dev.mateuy.safanoria.core.ReleaseRequest
import dev.mateuy.safanoria.core.Ship
import dev.mateuy.safanoria.core.ShipException
import dev.mateuy.safanoria.core.ShipResult
import dev.mateuy.safanoria.core.Status
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import okio.Path.Companion.toPath
import kotlin.time.Clock

/**
 * `safanoria-cli ship <parent> [--release <component>[=<version>]]…`: the partial release of a
 * parent (SPEC §8.1). Its branch merges into the main branch, optionally stamped, and back.
 */
class ShipCommand : RepositoryCommand(name = "ship") {
    override fun help(context: Context) =
        "Ship a parent in progress without finishing it: merge its branch, with the children done there, into the main " +
            "branch, logging it in the parent's Work Log and, with --release, stamping resolvedIn; then merge that back " +
            "into the parent's branch, where the remaining children and the parent's own steps go on. Everything on the " +
            "parent's branch ships, its own partial work too. Pushes nothing. Refuses, changing nothing, on uncommitted " +
            "changes in its worktree, a conflict, or tickets that wouldn't validate."

    private val idArgument by argument(name = "id", help = "Parent ticket id (on a terminal: asks, starting on this branch's ticket)").optional()
    private val releases by option(
        "--release", metavar = "COMPONENT[=VERSION]",
        help = "Also stamp this component (repeatable), as `release` does; the version is read from its source unless given",
    ).multiple()
    private val dryRun by option("--dry-run", help = "Check that it can be shipped, change nothing").flag()
    private val date by option("--date", hidden = true, help = "Today's date (tests)")

    override fun run() {
        val repo = repository
        val today = date ?: Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        val branches = Branches.read(repo) ?: throw PrintMessage(
            "Not shipped: needs a git repository with the branch '${repo.config.mainBranch}' (mainBranch).", 1, true,
        )
        val id = idArgument ?: askTicket(
            branches, "Ship which parent?", setOf(Status.IN_PROGRESS), "nothing to ship: no ticket in progress",
            default = runCatching { repo.git.currentBranch() }.getOrNull(),
        )
        val ready = when (val r = Ship.prepare(repo, branches, id)) {
            is ShipResult.Refused -> throw PrintMessage("Not shipped: ${r.reason}", 1, true)
            is ShipResult.Ready -> r
        }
        val requests = releases.map { r ->
            val (component, version) = r.split('=', limit = 2).let { it[0] to it.getOrNull(1) }
            ReleaseRequest(component, version)
        }
        val plan = try {
            Ship.plan(repo, ready, requests, today)
        } catch (e: ShipException) {
            fail(e)
        }
        val verb = if (dryRun) "would ship" else "shipped"
        plan.stamped.forEach { s ->
            s.warnings.forEach { echo("warning: $it", err = true) }
            val what = if (s.stamped.isEmpty()) "nothing to stamp" else "${if (dryRun) "would stamp" else "stamped"} ${s.stamped.joinToString()}"
            echo("${s.component} ${s.version}: $what")
        }
        if (dryRun) {
            echo("$verb '$id' to ${ready.mainBranch} with ${ready.children.joinToString()}, then merge ${ready.mainBranch} back into '$id'")
            return
        }
        val shipped = try {
            Ship.perform(repo, plan)
        } catch (e: ShipException) {
            fail(e)
        }
        echo("$verb '$id' to ${ready.mainBranch} with ${ready.children.joinToString()}: ${shipped.commit.take(7)} ${shipped.message}")
        echo("merged ${ready.mainBranch} back into '$id': ${shipped.back.take(7)}; its remaining children go on there")
        echo("nothing was pushed")
    }

    private fun fail(e: ShipException): Nothing {
        if (e.problems.isEmpty()) throw PrintMessage("Not shipped: ${e.message}", 1, true)
        echo("Not shipped: ${e.message}", err = true)
        e.problems.forEach { echo(it.copy(file = it.file?.let { p -> displayPath(p).toPath() }).toString(), err = true) }
        throw ProgramResult(1)
    }
}
