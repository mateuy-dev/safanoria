package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import dev.mateuy.safanoria.core.Branches
import dev.mateuy.safanoria.core.TicketGraph

/**
 * A command that shows tickets: by default each ticket's real copy from every local branch
 * (SPEC §14), so a started ticket shows its branch's status, not `main`'s `backlog`.
 */
abstract class AcrossBranchesCommand(name: String) : RepositoryCommand(name) {
    private val remote by option("--remote", help = "Also read remote-tracking branches (origin/*). Run git fetch first.").flag()
    private val checkout by option("--checkout", help = "Only the files in this checkout, as they are; no other branches").flag()

    /**
     * The tickets to show. Falls back to the checkout when there is nothing to read across (not a
     * git repository, or no mainBranch branch).
     */
    protected val graph: TicketGraph by lazy {
        if (checkout && remote) throw UsageError("Give --checkout or --remote, not both.").apply { context = currentContext }
        if (checkout) repository.graph else Branches.read(repository, remote)?.graph ?: repository.graph
    }
}
