package dev.mateuy.safanoria.core

public sealed interface ShipResult {
    public data class Ready(
        /** The parent whose branch ships. */
        val id: String,
        /** Its `done` children that reach `mainBranch` with it for the first time. */
        val children: List<String>,
        val mainBranch: String,
        val mainCommit: String,
        val branchCommit: String,
        /** Branch `<id>` merged into [mainBranch], before the Work Log entry and the stamps. */
        val tree: String,
    ) : ShipResult

    public data class Refused(val reason: String) : ShipResult
}

/** What [Ship.perform] did: the merge [commit] on `mainBranch` and the [back] merge on branch `<id>`. */
public data class Shipped(
    val commit: String,
    val message: String,
    val back: String,
    /** Per component released, the version and the stamped ids. */
    val stamped: List<ReleaseResult.Ready>,
)

/**
 * The partial release of a parent with `childrenMergeInto: parent` (SPEC §8.1): ships what its
 * branch has, the `done` children merged there included, without finishing it. Two commits, made
 * with git plumbing and moved in only when both exist: on `mainBranch` a merge of branch `<id>`
 * that also logs the partial release in the parent's Work Log and, optionally, stamps (§9); then
 * on branch `<id>` a merge of that commit back (`--no-ff`), which brings the stamps and leaves the
 * branch unmerged, so it stays the target of the remaining children (§14.1). Pushes nothing.
 */
public object Ship {
    public fun prepare(repository: Repository, branches: Branches, id: String): ShipResult {
        if (!isValidId(id)) return refused("'$id' is not a ticket id (SPEC §3)")
        val ticket = branches.graph.ticket(id) ?: return refused("no ticket '$id' on any branch")
        Finish.onlyRemote(ticket)?.let { return refused(it) }
        val f = ticket.frontmatter ?: return refused("'$id' has no readable frontmatter")
        val main = repository.config.mainBranch
        val children = branches.graph.children(ticket)
        if (children.isEmpty()) return refused("'$id' is not a parent: no ticket names it in parent")
        if (f.childrenMergeInto == "main") return refused("the children of '$id' merge into $main on their own (childrenMergeInto: main): nothing to ship")
        when (f.status) {
            Status.IN_PROGRESS -> {}
            Status.REVIEW -> return refused("'$id' is in review: merge it (safanoria-cli merge), which ships everything")
            else -> return refused("'$id' is ${f.status?.text ?: "without a readable status"}; only a parent in-progress ships part of its work")
        }
        if (ticket.branch != id) return refused("'$id' has no unmerged branch '$id' to ship")
        val git = repository.git
        Review.blocking(git, id)?.let { return refused(it) }
        return try {
            val branchCommit = git.commitId("refs/heads/$id") ?: return refused("no local branch '$id' to ship")
            val mainCommit = git.commitId("refs/heads/$main") ?: return refused("no local branch '$main' to ship into")
            val worktree = git.worktrees().firstOrNull { it.branch == id }?.path?.takeIf { repository.fileSystem.exists(it) }
            val changes = worktree?.let { Git(it).uncommitted() }.orEmpty()
            if (changes.isNotEmpty()) {
                return refused("$worktree has uncommitted changes (${changes.size} file${if (changes.size == 1) "" else "s"}): commit or discard them first")
            }
            val layout = Branches.layout(repository, git.worktrees()) ?: return refused("can't tell where this checkout is in git")
            val onMain = Repository(repository.root, GitTreeFileSystem(git, mainCommit, layout.top))
            val shipping = children.filter { c ->
                c.frontmatter?.status == Status.DONE && c.branch == id && onMain.ticket(c.fileId)?.frontmatter?.status != Status.DONE
            }.map { it.fileId }.sorted()
            if (shipping.isEmpty()) return refused("no child of '$id' is done on '$id' and not yet on $main: nothing to ship")
            when (val merge = git.mergeTree(mainCommit, branchCommit)) {
                is MergeTree.Conflict -> refused(
                    "merging '$id' into $main conflicts in ${merge.paths.joinToString()}: " +
                        "merge $main into '$id' and solve it there, then ship again",
                )
                is MergeTree.Clean -> ShipResult.Ready(id, shipping, main, mainCommit, branchCommit, merge.tree)
            }
        } catch (e: GitException) {
            refused(e.message ?: e.toString())
        }
    }

    /**
     * Each component's version as its source reads it on the merged branches, which is what
     * [plan] stamps when no version is given; null for a component without a source (external).
     */
    public fun versions(repository: Repository, ready: ShipResult.Ready): Map<String, VersionRead?> {
        val git = repository.git
        val layout = Branches.layout(repository, git.worktrees()) ?: throw ShipException("can't tell where this checkout is in git.")
        val merged = Repository(repository.root, GitTreeFileSystem(git, ready.tree, layout.top))
        return repository.config.components.mapValues { (_, c) -> c.version?.let { Versions.read(merged, it) } }
    }

    /**
     * The merged tree with the parent's Work Log entry and, for each of [releases], the stamps;
     * refused like [Release.prepare]. Writes only git objects, so a refusal changes nothing.
     */
    public fun plan(repository: Repository, ready: ShipResult.Ready, releases: List<ReleaseRequest>, today: String): ShipPlan {
        val git = repository.git
        val layout = Branches.layout(repository, git.worktrees()) ?: throw ShipException("can't tell where this checkout is in git.")
        val merged = Repository(repository.root, GitTreeFileSystem(git, ready.tree, layout.top))
        val texts = linkedMapOf<okio.Path, String>()
        fun current() = Repository(repository.root, OverlayFileSystem(merged.fileSystem, texts.mapKeys { it.key.normalized() }))

        val parent = merged.ticket(ready.id) ?: throw ShipException("the merge has no ticket '${ready.id}'.")
        val stamped = mutableListOf<ReleaseResult.Ready>()
        for (request in releases) {
            when (val r = Release.prepare(current(), request, today)) {
                is ReleaseResult.Refused -> throw ShipException("not stamped: ${r.reason}")
                is ReleaseResult.Ready -> { stamped += r; r.files.forEach { texts[it.path] = it.text } }
            }
        }
        val versions = stamped.joinToString { "${it.component} ${it.version}" }
        val note = "shipped to ${ready.mainBranch}: ${ready.children.joinToString { "`$it`" }}" + (if (versions.isEmpty()) "" else ", released in $versions")
        texts[parent.path] = try {
            TicketEditor(texts[parent.path] ?: parent.text).setField("updated", today).appendWorkLog(today, "ship", note).text
        } catch (e: TicketEditException) {
            throw ShipException(e.message ?: e.toString())
        }

        val result = current()
        val brought = git.changedPaths(ready.mainCommit, ready.tree, repository.config.dir).map { repository.root / it }
            .filter { result.fileSystem.exists(it) }
        val problems = Validator(result).validate((brought + texts.keys).distinct()).filter { it.severity == Severity.ERROR }
        if (problems.isNotEmpty()) throw ShipException("the shipped tickets wouldn't be valid", problems)

        val relative = texts.map { (path, text) -> path.relativeTo(repository.root).segments.joinToString("/") to text }
        val message = "${ready.id}: ship ${ready.children.joinToString()}" + (if (versions.isEmpty()) "" else " ($versions)")
        return ShipPlan(ready, treeWith(git, ready.tree, layout.sub, relative), message, stamped)
    }

    /**
     * Commits [plan] on `mainBranch`, then merges it back into branch `<id>`, and moves both:
     * by a fast-forward where a branch is checked out (which refuses when uncommitted changes
     * are in the way), else by moving the branch. A failure on `mainBranch` changes nothing.
     */
    public fun perform(repository: Repository, plan: ShipPlan): Shipped {
        val git = repository.git
        val ready = plan.ready
        val back = "${ready.id}: merge ${ready.mainBranch} back"
        val (commit, backCommit) = try {
            val c = git.commitTree(plan.tree, listOf(ready.mainCommit, ready.branchCommit), plan.message)
            c to git.commitTree(plan.tree, listOf(ready.branchCommit, c), back)
        } catch (e: GitException) {
            throw ShipException("nothing changed: ${e.message}")
        }
        try {
            move(repository, ready.mainBranch, commit, ready.mainCommit)
        } catch (e: GitException) {
            throw ShipException("nothing changed: ${e.message}")
        }
        try {
            move(repository, ready.id, backCommit, ready.branchCommit)
        } catch (e: GitException) {
            throw ShipException(
                "shipped to ${ready.mainBranch} (${commit.take(7)}), but '${ready.id}' didn't move: ${e.message}. " +
                    "Merge it back by hand: git merge --no-ff ${ready.mainBranch}, on '${ready.id}'",
            )
        }
        return Shipped(commit, plan.message, backCommit, plan.stamped)
    }

    private fun move(repository: Repository, branch: String, new: String, old: String) {
        val git = repository.git
        val checkedOut = git.worktrees().firstOrNull { it.branch == branch }?.path?.takeIf { repository.fileSystem.exists(it) }
        if (checkedOut != null) Git(checkedOut).run("merge", "-q", "--ff-only", new)
        else git.updateBranch(branch, new, old, "safanoria-cli ship")
    }

    private fun refused(reason: String) = ShipResult.Refused(reason)
}

/** What [Ship.perform] commits: [tree] on `mainBranch` with [message], and the components [stamped]. */
public data class ShipPlan(val ready: ShipResult.Ready, val tree: String, val message: String, val stamped: List<ReleaseResult.Ready>)

/** Why [Ship.plan] or [Ship.perform] didn't ship; [problems] when the tickets wouldn't validate. */
public class ShipException(message: String, public val problems: List<Diagnostic> = emptyList()) : Exception(message)
