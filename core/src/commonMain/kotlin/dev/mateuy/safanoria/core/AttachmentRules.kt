package dev.mateuy.safanoria.core

import okio.Path

/** SPEC §7.8 attachments: links into `<dir>/attachments/` must resolve; files should stay small. */
internal class AttachmentRules(private val repository: Repository) {
    private val fs = repository.fileSystem
    private val attachments: Path = repository.ticketDir / ATTACHMENTS

    fun findings(): List<Finding> = repository.tickets.flatMap(::missing) + large()

    /** Links from [ticket] to files under `attachments/` that don't exist. Code is skipped. */
    private fun missing(ticket: Ticket): List<Finding> {
        val out = mutableListOf<Finding>()
        val lines = ticket.text.split('\n')
        val start = Frontmatter.bounds(lines)?.closeLine ?: 0
        val dir = ticket.path.parent ?: return out
        var fence: String? = null
        for (i in start until lines.size) {
            val line = lines[i].trimEnd('\r')
            val marker = FENCE.find(line.trimStart())?.value
            if (fence != null) {
                if (marker != null && marker.first() == fence.first() && marker.length >= fence.length) fence = null
                continue
            }
            if (marker != null) { fence = marker; continue }
            val text = line.replace(CODE_SPAN) { " ".repeat(it.value.length) } // keep columns
            for (m in LINK.findAll(text)) {
                val target = m.groupValues[1].substringBefore('#').replace("%20", " ")
                if (target.isEmpty() || target.contains("://") || target.startsWith("/") || target.startsWith("mailto:")) continue
                val path = (dir / target).normalized()
                if (!path.isUnder(attachments.normalized()) || fs.exists(path)) continue
                out += Finding(Diagnostic(ticket.path, i + 1, m.range.first + 1, "attachment-missing",
                    "links to $target, which does not exist (§7.8)"))
            }
        }
        return out
    }

    /** Attachment files over [MAX_SIZE]: a warning on the file, caused by its ticket. */
    private fun large(): List<Finding> {
        if (!fs.exists(attachments)) return emptyList()
        return fs.listRecursively(attachments).mapNotNull { file ->
            val size = fs.metadata(file).takeIf { it.isRegularFile }?.size ?: return@mapNotNull null
            if (size <= MAX_SIZE) return@mapNotNull null
            val owner = file.relativeTo(attachments).segments.first()
            Finding(
                Diagnostic(file, null, null, "attachment-large",
                    "${size / 1024} KB; attachments should be under 1 MB, the repository keeps them forever (§7.8)", Severity.WARNING),
                setOf(repository.ticketDir / "$owner.md"),
            )
        }.toList()
    }

    private fun Path.isUnder(dir: Path): Boolean =
        root == dir.root && segments.size > dir.segments.size && segments.subList(0, dir.segments.size) == dir.segments

    companion object {
        const val ATTACHMENTS = "attachments"
        const val MAX_SIZE = 1024L * 1024
        private val FENCE = Regex("^(`{3,}|~{3,})")
        private val CODE_SPAN = Regex("(`+)[^`]*?\\1")
        /** `[text](target)` and `![alt](target "title")`; the target is group 1. */
        private val LINK = Regex("""!?\[[^\]]*]\(\s*<?([^)\s>]+)>?(?:\s+"[^"]*")?\s*\)""")
    }
}
