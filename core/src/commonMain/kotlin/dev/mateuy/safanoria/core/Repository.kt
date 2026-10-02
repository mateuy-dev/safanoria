package dev.mateuy.safanoria.core

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/** SPEC §3 id rule, as in schema/ticket.schema.json. */
public val ID_PATTERN: Regex = Regex("^[a-z](-?[a-z0-9])+$")

public fun isValidId(id: String): Boolean = id.length in 3..40 && ID_PATTERN.matches(id)

/**
 * A repository using Safanoria: its config, its tickets (read when first asked for, parsed
 * lazily) and git. The entry point for commands and apps.
 */
public class Repository(public val root: Path, public val fileSystem: FileSystem = SystemFileSystem) {
    public val configResult: ConfigResult by lazy { ConfigLoader.load(fileSystem, root) }

    /** The config, or the defaults when `safanoria.yaml` can't be read ([configResult] says why). */
    public val config: Config get() = configResult.config ?: Config(root / CONFIG_FILE, specVersion = null)

    public val ticketDir: Path get() = root / config.dir

    /** Ticket files: `<id>.md` with a valid id (SPEC §1). README.md, _TEMPLATE.md etc. are not tickets. */
    public fun ticketPaths(): List<Path> {
        if (!fileSystem.exists(ticketDir)) return emptyList()
        return fileSystem.list(ticketDir)
            .filter { it.name.endsWith(".md") && isValidId(it.name.removeSuffix(".md")) }
            .filter { fileSystem.metadata(it).isRegularFile }
            .sortedBy { it.name }
    }

    public val tickets: List<Ticket> by lazy { ticketPaths().map { Ticket(it, fileSystem.read(it) { readUtf8() }) } }

    public fun ticket(id: String): Ticket? = tickets.firstOrNull { it.fileId == id }

    /** The tickets with their relations derived (children, blocks, progress). */
    public val graph: TicketGraph by lazy { TicketGraph(tickets) }

    public val git: Git by lazy { Git(root) }

    public companion object {
        /** Opens the repository containing [start] (default: the working directory), or null. */
        public fun find(start: Path? = null, fileSystem: FileSystem = SystemFileSystem): Repository? {
            val from = fileSystem.canonicalize(start ?: ".".toPath())
            return ConfigLoader.findRoot(fileSystem, from)?.let { Repository(it, fileSystem) }
        }
    }
}
