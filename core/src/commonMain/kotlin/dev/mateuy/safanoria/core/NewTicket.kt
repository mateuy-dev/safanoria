package dev.mateuy.safanoria.core

import okio.Path

public data class NewTicketRequest(
    val title: String,
    /** The id to use; null to use [Ids.suggest]. */
    val id: String? = null,
    val parent: String? = null,
    val type: TicketType = TicketType.FEATURE,
    val priority: Priority = Priority.MEDIUM,
    val size: Size = Size.S,
    val area: List<String> = emptyList(),
    /** Text for `## Objective`; null keeps the template's. */
    val objective: String? = null,
)

/** A file to write: the new ticket, or the parent with its new Plan item. */
public data class PlannedFile(val path: Path, val text: String, val isNew: Boolean)

public sealed interface NewTicketResult {
    public data class Ready(val id: String, val idSuggested: Boolean, val files: List<PlannedFile>) : NewTicketResult
    public data class Refused(val reason: String) : NewTicketResult
}

/**
 * The mechanical part of creating a ticket (SPEC §11 Create): checks, the new file from the
 * template, the parent's Plan item. Writes nothing: the caller writes [NewTicketResult.Ready.files].
 */
public object NewTicket {
    public const val TEMPLATE_FILE: String = "_TEMPLATE.md"

    public fun prepare(repository: Repository, request: NewTicketRequest, today: String): NewTicketResult {
        val title = request.title.trim()
        if (title.isEmpty() || '\n' in title) return refused("the title must be one non-empty line")

        val config = repository.config
        val parent = request.parent?.let { id -> repository.ticket(id) ?: return refused("no ticket '$id' to be the parent") }
        parent?.frontmatter?.let { pf ->
            if (pf.parent != null) return refused("'${parent.fileId}' has a parent, so it can't have children (one level only, §8.1)")
            if (pf.type == TicketType.RESEARCH) return refused("'${parent.fileId}' is research, which can't have children (§6.2)")
        }

        val id = request.id ?: Ids.suggest(title, parent?.fileId)
            ?: return refused("no id can be made from the title; give one")
        if (!isValidId(id)) return refused("'$id' is not a valid id: 3-40 of a-z, 0-9 and '-', starting with a letter, no '--', not ending in '-' (§3)")
        val path = repository.ticketDir / "$id.md"
        if (repository.fileSystem.exists(path) || repository.tickets.any { it.frontmatter?.id?.value == id }) {
            return refused("ticket '$id' already exists; ids are never reused (§3)")
        }

        val components = config.components.keys
        request.area.firstOrNull { it !in components }?.let { return refused("'$it' is not a component: ${components.joinToString()}") }
        if (request.area.isEmpty() && components.size > 1) return refused("give the area: one or more of ${components.joinToString()} (§5)")

        val (templatePath, template) = template(repository, request.type)

        val text = try {
            TicketEditor(template).apply {
                setField("id", id)
                setField("type", request.type.text)
                setField("title", title)
                setField("status", Status.BACKLOG.text)
                setField("priority", request.priority.text)
                setField("size", request.size.text)
                if (request.area.isNotEmpty()) setList("area", request.area)
                setField("created", today)
                setField("updated", today)
                parent?.let { setField("parent", it.fileId) }
                request.objective?.let { replaceSectionIntro("Objective", it) }
            }.text
        } catch (e: TicketEditException) {
            return refused("the template ${templatePath ?: "(built-in)"} can't be filled: ${e.message}")
        }

        val files = mutableListOf(PlannedFile(path, text, isNew = true))
        if (parent != null) {
            val parentText = try {
                TicketEditor(parent.text).appendPlanItem("`$id`: $title").setField("updated", today).text
            } catch (e: TicketEditException) {
                return refused("can't add the child to '${parent.fileId}': ${e.message}")
            }
            files += PlannedFile(parent.path, parentText, isNew = false)
        }
        return NewTicketResult.Ready(id, idSuggested = request.id == null, files = files)
    }

    /**
     * The template for [type] (SPEC §1): the project's `_TEMPLATE.<type>.md`, else its
     * `_TEMPLATE.md`, else the built-in one for the type, else the built-in default. The path is
     * null for built-in ones.
     */
    public fun template(repository: Repository, type: TicketType): Pair<Path?, String> {
        val fs = repository.fileSystem
        for (name in listOf("_TEMPLATE.${type.text}.md", TEMPLATE_FILE)) {
            val path = repository.ticketDir / name
            if (fs.exists(path)) return path to fs.read(path) { readUtf8() }
        }
        return null to (Embedded.TYPE_TEMPLATES[type.text] ?: Embedded.TICKET_TEMPLATE)
    }

    private fun refused(reason: String) = NewTicketResult.Refused(reason)
}
