package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.Context
import dev.mateuy.safanoria.core.Repository
import dev.mateuy.safanoria.core.Status
import dev.mateuy.safanoria.core.isValidId
import dev.mateuy.safanoria.core.CONFIG_FILE
import dev.mateuy.safanoria.core.Install

/**
 * `safanoria-cli context`: run by the Claude Code SessionStart hook (README "Agent sessions"). On a
 * ticket's branch (SPEC §11.3: the branch name is the id) it prints the ticket and what the
 * session is expected to do with it; anywhere else it prints nothing. Never fails: a hook that
 * errors would interrupt every session.
 */
class ContextCommand : RepositoryCommand(name = "context") {
    override fun help(context: Context) =
        "Print the ticket of the current branch for an agent session (the SessionStart hook). Prints nothing " +
            "when the branch isn't a ticket id."

    override fun run() {
        val text = runCatching { context(repository) }.getOrNull() ?: return
        echo(text, trailingNewline = false)
    }

    private fun context(repo: Repository): String? {
        val branch = repo.git.currentBranch()
        if (!isValidId(branch)) return null
        val ticket = repo.ticket(branch) ?: return null
        val file = displayPath(ticket.path)
        return buildString {
            append("This session works on the Safanoria ticket `$branch`: this checkout is its branch, and the ticket is $file ")
            append("(format: ${Install.SKILL_DIR}/SPEC.md, settings: $CONFIG_FILE). Work as the user directs; there is no planning ")
            append("or approval step. Keep the ticket current with the `safanoria` skill: a short Work Log entry for each decision ")
            append("(what and why, not progress), committed together with the code it explains.")
            if (ticket.frontmatter?.status == Status.IN_PROGRESS) {
                append(" When you consider the implementation complete, run `safanoria-cli finish $branch` without being asked, ")
                append("so the ticket shows that it waits for review.")
            }
            append("\n\n")
            append("<ticket file=\"$file\">\n")
            append(ticket.text.trimEnd('\n'))
            append("\n</ticket>\n")
        }
    }
}
