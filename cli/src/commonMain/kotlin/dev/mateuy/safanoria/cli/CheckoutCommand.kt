package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import dev.mateuy.safanoria.core.Branches
import dev.mateuy.safanoria.core.GitException
import dev.mateuy.safanoria.core.Start
import dev.mateuy.safanoria.core.StartResult

class CheckoutCommand : RepositoryCommand(name = "checkout") {
    override fun help(context: Context) =
        "Create a local worktree for an existing ticket branch, using the configured worktree path. " +
            "Reuse the local branch or track its remote branch; preserve ticket status and history. Fetch remote branches first."

    private val id by argument(help = "Ticket id")
    private val dryRun by option("--dry-run", help = "Show what would be done, change nothing").flag()
    private val printPath by option("--print-path", help = "Print only the worktree path on stdout; messages go to stderr").flag()

    override fun run() {
        val repo = repository
        val branches = Branches.read(repo)
            ?: throw PrintMessage("Not checked out: needs a git repository with the branch '${repo.config.mainBranch}' (mainBranch).", 1, true)
        val ready = when (val result = Start.prepareCheckout(repo, branches, id)) {
            is StartResult.Refused -> throw PrintMessage("Not checked out: ${result.reason}", 1, true)
            is StartResult.Ready -> result
        }
        echo("${if (dryRun) "would add" else "add"} worktree ${ready.worktree} from ${ready.base}", err = printPath)
        if (dryRun) return
        try {
            Start.checkOut(repo, ready)
        } catch (e: GitException) {
            throw PrintMessage("Not checked out: ${e.message}", 1, true)
        }
        if (printPath) echo(ready.worktree.toString())
    }
}
