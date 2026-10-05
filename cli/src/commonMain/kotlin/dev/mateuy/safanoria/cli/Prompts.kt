package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.mordant.input.interactiveMultiSelectList
import com.github.ajalt.mordant.input.interactiveSelectList
import com.github.ajalt.mordant.rendering.TextStyles.dim
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.YesNoPrompt
import com.github.ajalt.mordant.terminal.prompt
import com.github.ajalt.mordant.widgets.SelectList
import dev.mateuy.safanoria.core.Branches
import dev.mateuy.safanoria.core.Status
import dev.mateuy.safanoria.core.Ticket
import dev.mateuy.safanoria.core.text

/** One option in a [Prompts.choose] list: [value] is returned, [label] and [description] are shown. */
data class Choice(val value: String, val label: String = value, val description: String? = null)

/**
 * Questions for a person at a terminal. Commands ask only when a value is missing, and only when
 * there are prompts: [CliContext.prompts] is null when stdin or stdout isn't a terminal, so
 * scripts and agents get the non-interactive behaviour (an error for a missing value).
 *
 * Cancelling (Ctrl-C, end of input) stops the command without changing anything. Esc doesn't:
 * Mordant 3.0.2's lists ignore a lone Esc on Linux native (seen in a pty), so it isn't advertised.
 */
interface Prompts {
    /** One of [choices], starting on [default] (a value). */
    fun choose(title: String, choices: List<Choice>, default: String? = null): String

    /** Zero or more of [choices], starting with [selected] (values) checked. */
    fun chooseMany(title: String, choices: List<Choice>, selected: List<String> = emptyList()): List<String>

    /** A line of text; empty input gives [default], or "" when there is none. */
    fun text(prompt: String, default: String? = null): String

    fun confirm(prompt: String, default: Boolean): Boolean
}

/** Thrown when the person cancels a prompt: the command stops, exit 130 like an interrupted program. */
fun cancelled(): Nothing = throw ProgramResult(130)

/** [Prompts] on a Mordant terminal: arrow-key lists for choices, line input for text. */
class TerminalPrompts(private val terminal: Terminal) : Prompts {
    override fun choose(title: String, choices: List<Choice>, default: String?): String {
        val start = choices.indexOfFirst { it.value == default }.coerceAtLeast(0)
        val lines = lines(choices)
        val line = terminal.interactiveSelectList {
            lines.forEach { addEntry(it) }
            title(title)
            startingCursorIndex(start)
            limit(LIST_HEIGHT)
        } ?: cancelled()
        return choices[lines.indexOf(line)].value
    }

    override fun chooseMany(title: String, choices: List<Choice>, selected: List<String>): List<String> {
        val lines = lines(choices)
        val chosen = terminal.interactiveMultiSelectList {
            choices.forEachIndexed { i, choice -> addEntry(SelectList.Entry(lines[i], selected = choice.value in selected)) }
            title("$title (space to select, enter to confirm)")
            limit(LIST_HEIGHT)
        } ?: cancelled()
        return choices.filterIndexed { i, _ -> lines[i] in chosen }.map { it.value }
    }

    override fun text(prompt: String, default: String?): String =
        terminal.prompt(prompt, default = default ?: "", showDefault = !default.isNullOrEmpty()) ?: cancelled()

    override fun confirm(prompt: String, default: Boolean): Boolean =
        YesNoPrompt(prompt, terminal, default = default).ask() ?: cancelled()

    /** Dim keeps the terminal's own foreground, so it reads on light and dark themes; without colour Mordant drops it. */
    private fun lines(choices: List<Choice>) = choiceLines(choices, terminal.updateSize().width - LIST_GUTTER) { dim(it) }

    private companion object {
        const val LIST_HEIGHT = 12

        /** Columns the list puts before an entry: the cursor and, when choosing many, the check mark. */
        const val LIST_GUTTER = 4
    }
}

/**
 * The line shown for each of [choices]: the labels in a column, each description beside its label
 * in the [secondary] style and cut to fit [width]. One line per entry, so the labels are what a
 * person scans, and the column keeps them apart from the descriptions when there is no colour.
 */
internal fun choiceLines(choices: List<Choice>, width: Int, secondary: (String) -> String): List<String> {
    val column = choices.maxOfOrNull { it.label.length } ?: 0
    val room = width - column - LABEL_GAP.length
    return choices.map { choice ->
        val description = choice.description.orEmpty()
        when {
            description.isEmpty() || room < 1 -> choice.label
            description.length <= room -> choice.label.padEnd(column) + LABEL_GAP + secondary(description)
            else -> choice.label.padEnd(column) + LABEL_GAP + secondary(description.take(room - 1) + "…")
        }
    }
}

private const val LABEL_GAP = "  "

/**
 * The id of a ticket in one of [statuses], chosen by the person; a usage error for a missing id
 * without prompts, a refusal ([none]) when no ticket qualifies.
 */
internal fun RepositoryCommand.askTicket(
    branches: Branches,
    title: String,
    statuses: Set<Status>,
    none: String,
    default: String? = null,
): String {
    val prompts = prompts ?: throw UsageError("missing argument <id>").apply { context = currentContext }
    val tickets = branches.tickets.filter { it.frontmatter?.status in statuses }
    if (tickets.isEmpty()) throw PrintMessage(none, 1, true)
    return prompts.choose(title, tickets.map(::ticketChoice), default)
}

/** A ticket in a list: its id, with status and title beside it. */
internal fun ticketChoice(ticket: Ticket): Choice {
    val fm = ticket.frontmatter
    return Choice(ticket.fileId, description = listOfNotNull(fm?.status?.text, fm?.title?.value).joinToString(" · "))
}
