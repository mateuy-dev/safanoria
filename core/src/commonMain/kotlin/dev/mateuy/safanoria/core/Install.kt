package dev.mateuy.safanoria.core

import okio.FileSystem
import okio.Path

/** What `init`/`update` would do to one file. */
public enum class FileAction {
    /** Missing: write it. */
    CREATE,
    /** Safanoria's own file, different from this version's: replace it. */
    REPLACE,
    /** Already as it should be. */
    SAME,
    /** The project's file, different from what Safanoria would write: only after asking. */
    DIFFERS,
}

public data class FileChange(
    val path: Path,
    /** The content Safanoria would write (for CLAUDE.md: the existing text plus the paragraph). */
    val text: String,
    val action: FileAction,
    /** True for the skill and spec, which Safanoria replaces; false for files the project owns. */
    val managed: Boolean,
)

/** A component for a new `safanoria.yaml`: with a version source, or external when [source] is null. */
public data class ComponentSpec(val name: String, val source: VersionSource.Property?)

/**
 * Setting up and updating a project (SPEC §13): the skill and SPEC.md, which Safanoria manages and
 * marks with its version, and the files the project owns once created (templates, the ticket
 * directory's README, CLAUDE.md, safanoria.yaml). Writes nothing: the caller asks and writes.
 */
public object Install {
    public const val SKILL_DIR: String = ".claude/skills/safanoria"
    public const val CLAUDE_FILE: String = "CLAUDE.md"
    private val MARKER = Regex("""<!-- safanoria (\S+) -->""")

    /** The marker line installed copies end with. */
    public fun marker(version: String = Embedded.VERSION): String = "<!-- safanoria $version -->"

    /** The Safanoria version an installed copy came from; null when it has no marker (copied by hand). */
    public fun installedVersion(text: String): String? = MARKER.findAll(text).lastOrNull()?.groupValues?.get(1)

    private fun marked(text: String) = text.trimEnd('\n') + "\n\n" + marker() + "\n"

    /** The managed files, with their content for this version. */
    public fun managedFiles(): Map<String, String> =
        mapOf("SKILL.md" to marked(Embedded.SKILL), "SPEC.md" to marked(Embedded.SPEC))

    /** The templates a project gets: the default and one per type with a built-in template. */
    public fun templates(): Map<String, String> =
        mapOf(NewTicket.TEMPLATE_FILE to Embedded.TICKET_TEMPLATE) +
            Embedded.TYPE_TEMPLATES.mapKeys { (type, _) -> "_TEMPLATE.$type.md" }

    public fun ticketReadme(): String =
        "# Tickets\n\nThis directory is tracked with [Safanoria](https://github.com/mateuy-dev/safanoria):\n" +
            "one markdown file per ticket, `<id>.md`. The format is in `$SKILL_DIR/SPEC.md`.\n\n" +
            "- `_TEMPLATE.md`: template for new tickets; `_TEMPLATE.<type>.md` for one type.\n" +
            "- `attachments/<id>/`: files a ticket links to.\n\n" +
            "`safanoria list`, `safanoria board` and `safanoria validate` show and check them.\n"

    /** The CLAUDE.md paragraph that points agents to the skill (README "Adding Safanoria"). */
    public fun claudeParagraph(dir: String): String =
        "Work is tracked as Safanoria tickets in `$dir/<id>.md` (settings: `$CONFIG_FILE`). The ticket id is " +
            "also the branch name. When creating, planning or working on a ticket, use the `safanoria` skill.\n"

    /** A new `safanoria.yaml`. */
    public fun config(dir: String, components: List<ComponentSpec>, mainBranch: String = "main"): String = buildString {
        append("# yaml-language-server: \$schema=https://raw.githubusercontent.com/mateuy-dev/safanoria/main/schema/safanoria.schema.json\n")
        append("safanoria: $SPEC_VERSION\n")
        append("dir: $dir\n")
        append("mainBranch: $mainBranch\n")
        append("components:\n")
        for (c in components) {
            append("  ${c.name}:\n")
            if (c.source == null) append("    external: true\n")
            else append("    version: { file: ${c.source.file}, property: ${c.source.property} }\n")
        }
    }

    /** Every file Safanoria installs or creates in [root], and what to do with each. */
    public fun plan(fileSystem: FileSystem, root: Path, dir: String): List<FileChange> {
        fun read(path: Path) = if (fileSystem.exists(path)) fileSystem.read(path) { readUtf8() } else null
        fun change(path: Path, wanted: String, managed: Boolean): FileChange {
            val existing = read(path)
            val action = when {
                existing == null -> FileAction.CREATE
                existing.replace("\r\n", "\n") == wanted -> FileAction.SAME
                managed -> FileAction.REPLACE
                else -> FileAction.DIFFERS
            }
            return FileChange(path, wanted, action, managed)
        }

        val skillDir = root / SKILL_DIR
        val ticketDir = root / dir
        val claude = root / CLAUDE_FILE
        val claudeText = read(claude)
        val claudeChange = when {
            claudeText == null -> FileChange(claude, claudeParagraph(dir), FileAction.CREATE, managed = false)
            "`safanoria` skill" in claudeText -> FileChange(claude, claudeText, FileAction.SAME, managed = false)
            else -> FileChange(claude, claudeText.trimEnd('\n') + "\n\n" + claudeParagraph(dir), FileAction.DIFFERS, managed = false)
        }
        return managedFiles().map { (name, text) -> change(skillDir / name, text, managed = true) } +
            templates().map { (name, text) -> change(ticketDir / name, text, managed = false) } +
            listOf(change(ticketDir / "README.md", ticketReadme(), managed = false)).map {
                // A README that exists is the project's, whatever it says.
                if (it.action == FileAction.DIFFERS) it.copy(action = FileAction.SAME) else it
            } +
            claudeChange
    }
}
