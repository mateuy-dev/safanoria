package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import dev.mateuy.safanoria.core.BranchView
import dev.mateuy.safanoria.core.Branches
import dev.mateuy.safanoria.core.Herdr
import dev.mateuy.safanoria.core.HerdrException
import dev.mateuy.safanoria.core.SessionAgent
import dev.mateuy.safanoria.core.Status

class RefineCommand : RepositoryCommand(name = "refine") {
    override fun help(context: Context) =
        "Open a Herdr agent session to refine a backlog or ready ticket in the existing mainBranch checkout."

    private val id by argument(help = "Ticket to refine")
    private val agentOption by option("--agent", help = "Session agent (defaults to defaultAgent, then claude)")
        .choice(SessionAgent.entries.associateBy { it.option })

    override fun run() {
        if (!Herdr.inside()) throw PrintMessage("Refinement needs a Herdr pane (HERDR_ENV=1).", 1, true)
        val repo = repository
        val main = repo.config.mainBranch
        val dir = BranchView.open(repo, main)?.worktree
            ?: throw PrintMessage("Refinement needs an existing checkout of '$main'.", 1, true)
        val graph = Branches.read(repo, remote = true)?.graph
        val ticket = graph?.ticket(id) ?: throw PrintMessage("No ticket '$id'.", 1, true)
        if (ticket.branch != main || ticket.frontmatter?.status !in setOf(Status.BACKLOG, Status.READY)) {
            throw PrintMessage("Only backlog or ready tickets on '$main' can be refined. Check '$id' with safanoria-cli list.", 1, true)
        }
        val agent = SessionAgent.resolve(agentOption, repo.config.defaultAgent)
        val prompt = "Refine ticket $id on the configured main branch using the safanoria skill's Refine flow. " +
            "Work with the user to clarify open questions, then validate and commit only the affected ticket files."
        try {
            echo(Herdr().open(dir, "refine-$id", prompt, Herdr.opensWorkspace(graph, id), agent))
        } catch (e: HerdrException) {
            throw PrintMessage("Refinement launch failed: ${e.message}", 1, true)
        }
    }
}
