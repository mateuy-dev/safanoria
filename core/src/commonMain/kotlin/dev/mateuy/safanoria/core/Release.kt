package dev.mateuy.safanoria.core

public data class ReleaseRequest(
    val component: String,
    /** `MAJOR.MINOR.PATCH`; null to read it from the component's `version` source. */
    val version: String? = null,
    /** Stamp only these tickets (for external components, §9); null for every eligible one. */
    val only: List<String>? = null,
)

/**
 * A shipped parent's branch after [Release.mergeBack]: merged in [commit] (null in a dry run), or,
 * with [warning], left as it was.
 */
public data class MergedBack(val parent: String, val commit: String?, val warning: String?)

public sealed interface ReleaseResult {
    public data class Ready(
        val component: String,
        val version: Version,
        /** True when read from the component's source, false when given. */
        val versionFromSource: Boolean,
        /** Ids of the stamped tickets, in id order; empty when there is nothing to stamp. */
        val stamped: List<String>,
        val files: List<PlannedFile>,
        val warnings: List<String>,
        /** Ids of the tickets set `done` first: merged, but still in `review` ([Finish.merged]). */
        val promoted: List<String> = emptyList(),
    ) : ReleaseResult

    public data class Refused(val reason: String) : ReleaseResult
}

/**
 * Stamping (SPEC §9): `resolvedIn.<c>: <v>` and a `release · <c> <v>` Work Log entry on every
 * `done` ticket with `<c>` in its area and no version for it yet. Writes nothing: the caller
 * writes [ReleaseResult.Ready.files]. All or nothing: any refusal stamps no ticket.
 */
public object Release {
    /**
     * With [branches], on `mainBranch`: tickets merged there but still in `review` are set `done`
     * first, in the same files, so a forgotten one isn't left out of the version.
     */
    public fun prepare(repository: Repository, request: ReleaseRequest, today: String, branches: Branches? = null): ReleaseResult {
        val main = repository.config.mainBranch
        val merged = branches?.takeIf { it.branches.first() == main }?.let { b ->
            Finish.merged(b).filter { b.graph.ticket(it)?.branch == main && repository.ticket(it) != null }
        }.orEmpty()
        if (merged.isEmpty()) return stamp(repository, request, today)

        val texts = linkedMapOf<okio.Path, String>()
        try {
            for (id in merged) {
                val ticket = repository.ticket(id)!!
                texts[ticket.path] = Finish.edit(texts[ticket.path] ?: ticket.text, Status.DONE, today)
                val parent = repository.graph.parent(ticket) ?: continue
                texts[parent.path] = Finish.checkInParent(texts[parent.path] ?: parent.text, parent.path, id, today)
            }
        } catch (e: TicketEditException) {
            return refused("can't set a merged ticket done: ${e.message}")
        }
        val promoted = Repository(repository.root, OverlayFileSystem(repository.fileSystem, texts.mapKeys { it.key.normalized() }))
        return when (val r = stamp(promoted, request, today)) {
            is ReleaseResult.Refused -> r
            is ReleaseResult.Ready -> {
                val stamped = r.files.map { it.path }.toSet()
                r.copy(files = r.files + texts.filterKeys { it !in stamped }.map { (path, text) -> PlannedFile(path, text, isNew = false) }, promoted = merged)
            }
        }
    }

    private fun stamp(repository: Repository, request: ReleaseRequest, today: String): ReleaseResult {
        val config = repository.config
        val c = request.component
        val component = config.components[c]
            ?: return refused("'$c' is not a component in $CONFIG_FILE: ${config.components.keys.joinToString()}")

        val warnings = mutableListOf<String>()
        val fromSource = component.version?.let { Versions.read(repository, it) }
        val version = when {
            request.version != null -> {
                val given = Version.parse(request.version)
                    ?: return refused("'${request.version}' is not a version: MAJOR.MINOR.PATCH, e.g. 4.3.0")
                if (fromSource is VersionRead.Found && fromSource.version != given) {
                    warnings += "${component.version!!.file} says ${fromSource.version}, stamping $given as given"
                }
                given
            }
            component.external -> return refused("'$c' is external: give the version it was released as (§9)")
            fromSource == null -> return refused("'$c' has no version source in $CONFIG_FILE: give the version")
            fromSource is VersionRead.Failed -> return refused("can't read the version of '$c': ${fromSource.reason}")
            else -> (fromSource as VersionRead.Found).version
        }

        // Versions only go up per component: stamping 4.2.9 after 4.3.0 would say older releases had the change.
        val latest = repository.tickets.mapNotNull { t -> t.frontmatter?.resolvedIn?.get(c)?.value?.let(Version::parse) }.maxOrNull()
        if (latest != null && version < latest) return refused("$c $version is lower than $latest, already stamped on other tickets")

        val eligible = repository.tickets.filter { reasonNotEligible(it, c, config) == null }
        val tickets = if (request.only == null) eligible else {
            request.only.distinct().map { id ->
                val t = repository.ticket(id) ?: return refused("no ticket '$id'")
                reasonNotEligible(t, c, config)?.let { return refused("'$id' can't be stamped with $c: $it") }
                t
            }.sortedBy { it.fileId }
        }

        val files = tickets.map { t ->
            val text = try {
                TicketEditor(t.text)
                    .setMapEntry("resolvedIn", c, version.toString())
                    .setField("updated", today)
                    .appendWorkLog(today, "release", "$c $version")
                    .text
            } catch (e: TicketEditException) {
                return refused("can't stamp '${t.fileId}': ${e.message}")
            }
            PlannedFile(t.path, text, isNew = false)
        }
        return ReleaseResult.Ready(c, version, request.version == null, tickets.map { it.fileId }, files, warnings)
    }

    /**
     * After stamping [ready] on `mainBranch`: merges `mainBranch`, with the stamps, back (`--no-ff`)
     * into the local branch of every `in-progress` parent with `childrenMergeInto: parent` whose
     * branch has a stamped ticket, its child (one shipped, §8.1). There the children are read from
     * (§14.1), so without it they would show unreleased until the next merge of `mainBranch`.
     *
     * Stamping doesn't commit, so the merge takes the committed `mainBranch` with [ready]'s files on
     * top: the stamps are in the merge's tree, and the release commit, making the same changes,
     * merges cleanly into the branch later. Local branches only, pushes nothing; a branch with
     * uncommitted changes in its worktree or a conflicting merge is skipped, untouched. With
     * [dryRun], checks the same and moves nothing. Nothing when [branches] isn't on `mainBranch`.
     */
    public fun mergeBack(repository: Repository, branches: Branches, ready: ReleaseResult.Ready, dryRun: Boolean = false): List<MergedBack> {
        val main = repository.config.mainBranch
        if (ready.stamped.isEmpty() || branches.branches.first() != main) return emptyList()
        val graph = branches.graph
        // A child stamped here whose real copy is on its parent's branch: it shipped from there.
        val parents = ready.stamped.mapNotNull { id ->
            graph.ticket(id)?.let { c -> graph.parent(c)?.takeIf { it.fileId == c.branch } }
        }.distinctBy { it.fileId }.filter { p ->
            val f = p.frontmatter
            f?.status == Status.IN_PROGRESS && f.childrenMergeInto != "main" && p.branch == p.fileId
        }.sortedBy { it.fileId }
        if (parents.isEmpty()) return emptyList()

        val git = repository.git
        val note = "released ${ready.component} ${ready.version}"
        val stamped by lazy {
            val layout = Branches.layout(repository, git.worktrees()) ?: throw GitException("can't tell where this checkout is in git")
            val mainCommit = git.commitId("refs/heads/$main") ?: throw GitException("no local branch '$main'")
            val relative = ready.files.map { it.path.relativeTo(repository.root).segments.joinToString("/") to it.text }
            mainCommit to git.commitTree(treeWith(git, mainCommit, layout.sub, relative), listOf(mainCommit), "$main: $note")
        }
        return parents.map { p ->
            val id = p.fileId
            fun skipped(why: String) = MergedBack(id, null, "not merged back: $why; merge $main into it by hand once the release is committed")
            try {
                val branchCommit = git.commitId("refs/heads/$id") ?: return@map skipped("no local branch '$id'")
                val worktree = git.worktrees().firstOrNull { it.branch == id }?.path?.takeIf { repository.fileSystem.exists(it) }
                val changes = worktree?.let { Git(it).uncommitted() }.orEmpty()
                if (changes.isNotEmpty()) return@map skipped("$worktree has uncommitted changes")
                Review.blocking(git, id)?.let { return@map skipped(it) }
                val (mainCommit, stampedCommit) = stamped
                when (val merge = git.mergeTree(branchCommit, stampedCommit)) {
                    is MergeTree.Conflict -> skipped("it conflicts in ${merge.paths.joinToString()}")
                    is MergeTree.Clean -> {
                        if (dryRun) return@map MergedBack(id, null, null)
                        val commit = git.commitTree(merge.tree, listOf(branchCommit, mainCommit), "$id: merge $main back ($note)")
                        Ship.move(repository, id, commit, branchCommit)
                        MergedBack(id, commit, null)
                    }
                }
            } catch (e: GitException) {
                skipped(e.message ?: e.toString())
            }
        }
    }

    /**
     * The components a `done` [ticket] is still to be released in: those stamping would give it a
     * version for (§9). Empty when it is released everywhere, and when it never gets a version:
     * not `done`, `research`, or no component of the project.
     */
    public fun pending(ticket: Ticket, config: Config): List<String> {
        val f = ticket.frontmatter ?: return emptyList()
        if (f.status != Status.DONE || f.type == TicketType.RESEARCH) return emptyList()
        return area(f, config).filter { it in config.components && f.resolvedIn?.get(it)?.value == null }
    }

    /** The `area`, or the project's component when it has only one and the ticket names none. */
    private fun area(f: Frontmatter, config: Config): List<String> =
        f.area.map { it.value }.ifEmpty { config.components.keys.toList().takeIf { it.size == 1 } ?: emptyList() }

    /** Null when [ticket] gets `resolvedIn.<component>` in this release (§9), else why not. */
    private fun reasonNotEligible(ticket: Ticket, component: String, config: Config): String? {
        val f = ticket.frontmatter ?: return "its frontmatter can't be read"
        if (f.status != Status.DONE) return "it is ${f.status?.text ?: "not done"}, not done"
        if (f.type == TicketType.RESEARCH) return "research tickets never get resolvedIn"
        val area = area(f, config)
        if (component !in area) return "'$component' is not in its area (${area.joinToString().ifEmpty { "none" }})"
        f.resolvedIn?.get(component)?.value?.let { return "it already has $component $it" }
        return null
    }

    private fun refused(reason: String) = ReleaseResult.Refused(reason)
}
