package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import dev.mateuy.safanoria.core.SessionAgent
import dev.mateuy.safanoria.core.Repository
import dev.mateuy.safanoria.core.Review
import dev.mateuy.safanoria.core.Status
import dev.mateuy.safanoria.core.isValidId
import dev.mateuy.safanoria.core.CONFIG_FILE
import dev.mateuy.safanoria.core.Embedded
import dev.mateuy.safanoria.core.Install
import dev.mateuy.safanoria.core.SystemFileSystem

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

    private val agent by option("--agent", help = "Agent whose installed skill to discover (default: claude)")
        .choice(SessionAgent.entries.associateBy { it.option })

    override fun run() {
        val text = runCatching { context(repository) }.getOrNull() ?: return
        echo(text, trailingNewline = false)
    }

    private fun context(repo: Repository): String? {
        val review = Review.state(repo.root)
        val branch = review?.id ?: repo.git.currentBranch()
        if (!isValidId(branch)) return null
        val ticket = repo.ticket(branch) ?: return null
        val file = displayPath(ticket.path)
        // Claude Code prefers the user's skill to the project's when both exist.
        val skillDir = (if (agent == SessionAgent.CODEX)
            listOfNotNull(repo.root / Install.CODEX_SKILL_DIR, agentsDir?.let { it / Install.GLOBAL_SKILL_DIR })
        else listOfNotNull(claudeDir?.let { it / Install.GLOBAL_SKILL_DIR }, repo.root / Install.SKILL_DIR))
            .firstOrNull { SystemFileSystem.exists(it / "SKILL.md") }
        val skillVersion = skillDir?.let { Install.skillVersion(SystemFileSystem, it) }
        return buildString {
            if (review != null) {
                val against = review.since?.let { "after $it" } ?: "against ${review.target}"
                append("This worktree is in review mode (`safanoria-cli review`): HEAD is detached at ${review.base.take(7)} and the ")
                append("ticket's changes $against are staged, for the human to review in their IDE; branch `$branch` is unchanged. ")
                append("Don't edit or commit while in review mode. When the human says the review is done (or asks to look at their ")
                append("`REVIEW:` comments, or to merge), run `safanoria-cli review --end` first: it goes back to the branch and leaves ")
                append("the human's edits uncommitted. Then act on them as the safanoria skill says.\n\n")
            }
            append("This session works on the Safanoria ticket `$branch`: this checkout is its branch, and the ticket is $file ")
            append("(format: ${skillDir?.let { displayPath(it / "SPEC.md") } ?: "the safanoria skill's SPEC.md"}, settings: $CONFIG_FILE). ")
            append("Work as the user directs; there is no planning ")
            append("or approval step. Keep the ticket current with the `safanoria` skill: a short Work Log entry for each decision ")
            append("(what and why, not progress), committed together with the code it explains.")
            if (ticket.frontmatter?.status == Status.IN_PROGRESS) {
                append(" When you consider the implementation complete, run `safanoria-cli finish $branch` without being asked, ")
                append("so the ticket shows that it waits for review.")
            }
            append("\n\n")
            when {
                skillDir == null -> append(
                    "The `safanoria` skill isn't installed. Tell the user: `safanoria-cli update --global` installs it for all projects.\n\n",
                )
                skillVersion != Embedded.VERSION -> append(
                    "The `safanoria` skill in ${displayPath(skillDir)} is from Safanoria $skillVersion, but safanoria-cli is " +
                        "${Embedded.VERSION}: they may disagree. Tell the user: `safanoria-cli update --global` installs the " +
                        "current one for all projects; update an installed project copy with `safanoria-cli update`.\n\n",
                )
            }
            append("<ticket file=\"$file\">\n")
            append(ticket.text.trimEnd('\n'))
            append("\n</ticket>\n")
        }
    }
}
