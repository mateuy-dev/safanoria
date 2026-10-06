package dev.mateuy.safanoria.core

import okio.FileSystem
import okio.Path

/** The same ticket file created separately on two branches (SPEC §14.3). */
public data class CreatedTwice(val id: String, val branch: String, val otherBranch: String)

/**
 * The tickets of every branch of a repository, and each ticket's real copy (SPEC §14.1).
 * Branches checked out in a worktree are read from its files (uncommitted edits included), the
 * others from git without checking them out.
 */
public class Branches private constructor(
    private val repository: Repository,
    private val git: Git,
    /** Every branch by name without its remote, the current checkout first. */
    private val sources: Map<String, Source>,
    private val mainBranch: String,
    /** The repository root relative to the git top level (`""` when they are the same). */
    private val sub: String,
    /** Remote-tracking refs by branch name (`main` → `origin/main`); empty when they weren't read. */
    private val remoteRefs: Map<String, List<String>> = emptyMap(),
) {
    /**
     * One branch: [ref] is what git calls it (`main`, `origin/main`, `HEAD`). For a branch
     * checked out in a worktree, [repository] reads its files and [committed] its last commit.
     */
    internal class Source(val ref: String, val repository: Repository, val committed: Lazy<Repository>? = null)

    /** Copies by id, then by branch name, in [sources] order. */
    private val copies: Map<String, Map<String, Ticket>> by lazy {
        val out = mutableMapOf<String, MutableMap<String, Ticket>>()
        for ((name, source) in sources) for (t in source.repository.tickets) out.getOrPut(t.fileId) { linkedMapOf() }[name] = t
        out
    }

    private val merged = mutableMapOf<String, Set<String>>() // by target ref

    /** Every id that has a ticket file on some branch (SPEC §14.3: those ids are taken). */
    public val ids: Set<String> get() = copies.keys

    /** The branches read, the current checkout first. */
    public val branches: List<String> get() = sources.keys.toList()

    /** Each ticket's real copy, sorted by id, with paths as in this checkout. */
    public val tickets: List<Ticket> by lazy { copies.keys.sorted().map(::resolve) }

    public val graph: TicketGraph by lazy { TicketGraph(tickets) }

    private fun resolve(id: String): Ticket {
        val c = copies.getValue(id)
        val real = realBranch(id)
        val target = target(id)
        val (branch, copy) = listOf(real, target).firstNotNullOfOrNull { b -> c[b]?.let { b to it } }
            ?: c.entries.first().toPair()
        val path = repository.root / copy.path.relativeTo(sources.getValue(branch).repository.root)
        return Ticket(path, copy.text, branch, onlyOnBranch = branch != real && branch != target)
    }

    /**
     * Rules 1–2: branch `<id>` while it isn't merged into the target or has uncommitted edits to
     * the ticket, else the target. A branch just started has no commits yet, so git sees it as
     * merged; its uncommitted edits can't be.
     */
    private fun realBranch(id: String, depth: Int = 0): String {
        val target = target(id, depth)
        return if (id != target && id in sources && (!isMerged(id, target) || hasUncommittedEdits(id))) id else target
    }

    private fun hasUncommittedEdits(id: String): Boolean {
        val committed = sources.getValue(id).committed ?: return false
        val copy = copies[id]?.get(id) ?: return false
        return committed.value.ticket(id)?.text != copy.text
    }

    /** The parent's real branch when children merge into it, else [mainBranch]. Parents are one level (§8.1). */
    private fun target(id: String, depth: Int = 0): String {
        if (depth > 0) return mainBranch
        val parent = anyCopy(id)?.frontmatter?.parent?.value ?: return mainBranch
        val parentCopies = copies[parent] ?: return mainBranch
        val parentBranch = realBranch(parent, depth + 1)
        val parentTicket = parentCopies[parentBranch] ?: parentCopies[mainBranch] ?: parentCopies.values.first()
        return if (parentTicket.frontmatter?.childrenMergeInto == "main") mainBranch else parentBranch
    }

    /** The branch ticket [id] starts from and merges into (SPEC §11.2, §14). */
    public fun targetBranch(id: String): String = target(id)

    /** The copy to read `parent` from: branch `<id>`, else [mainBranch], else any. */
    private fun anyCopy(id: String): Ticket? = copies[id]?.let { it[id] ?: it[mainBranch] ?: it.values.firstOrNull() }

    private fun isMerged(branch: String, into: String): Boolean {
        val target = sources[into] ?: return false
        return sources.getValue(branch).ref in merged.getOrPut(target.ref) { git.mergedInto(target.ref) }
    }

    /**
     * Ids whose file exists on two branches but not at their merge-base (SPEC §14.3). Each copy
     * is compared with the real one; identical copies are skipped, so this costs about one git
     * call per changed copy.
     */
    public fun createdTwice(): List<CreatedTwice> {
        val bases = mutableMapOf<Pair<String, String>, String?>()
        // Files of a directory at a merge-base: one ls-tree per base and directory, not a call per ticket.
        val listings = mutableMapOf<Pair<String, String>, Set<String>>()
        fun hasPath(base: String, path: String): Boolean {
            val dir = path.substringBeforeLast('/', "")
            return path.substringAfterLast('/') in listings.getOrPut(base to dir) { git.tree(base, dir)?.map { it.name }?.toSet().orEmpty() }
        }
        val out = mutableListOf<CreatedTwice>()
        for ((id, c) in copies) {
            if (c.size < 2) continue
            val anchor = realBranch(id).takeIf { it in c } ?: mainBranch.takeIf { it in c } ?: c.keys.first()
            val anchorCopy = c.getValue(anchor)
            for ((other, copy) in c) {
                if (other == anchor || copy.text == anchorCopy.text) continue
                val refs = sources.getValue(anchor).ref to sources.getValue(other).ref
                val base = bases.getOrPut(refs) { git.mergeBase(refs.first, refs.second) }
                // A shallow clone (CI) has branch tips without the history that joins them: can't tell.
                if (base == null && shallow) continue
                if (base == null || !hasPath(base, treePath(other, copy))) out += CreatedTwice(id, anchor, other)
            }
        }
        return out.sortedWith(compareBy({ it.id }, { it.otherBranch }))
    }

    private val shallow: Boolean by lazy { git.isShallow() }

    /**
     * The local branch that has ticket [id] when no remote does and this checkout doesn't either
     * (e.g. created with `new --on main`, and `main` not pushed since): whoever gets this
     * checkout's branch from the remote, CI first, won't find it. Null when it is pushed, here,
     * unknown, or when there are no remote-tracking branches to compare with.
     */
    public fun onlyLocal(id: String): String? {
        if (remoteRefs.isEmpty()) return null
        val c = copies[id] ?: return null
        val here = sources.keys.first()
        if (here in c) return null
        val pushed = c.any { (branch, copy) ->
            val path = treePath(branch, copy)
            sources.getValue(branch).ref != branch || remoteRefs[branch].orEmpty().any { git.hasPath(it, path) }
        }
        return if (pushed) null else c.keys.first()
    }

    private fun treePath(branch: String, copy: Ticket): String {
        val inRoot = copy.path.relativeTo(sources.getValue(branch).repository.root).segments
        return (listOfNotNull(sub.ifEmpty { null }) + inRoot).joinToString("/")
    }

    public companion object {
        /**
         * Reads [repository]'s branches: local ones, and with [remote] remote-tracking ones too
         * (a local branch wins over a remote one with the same name). Null when there is nothing
         * to read across: not a git repository, or no [Config.mainBranch] branch (e.g. a CI
         * checkout of one detached commit). Callers then use the checkout as it is.
         */
        /** The ids [read] finds on every branch, remote-tracking ones too: what `validate` counts as known (§14). */
        public fun ids(repository: Repository): Set<String> = read(repository, remote = true)?.ids.orEmpty()

        public fun read(repository: Repository, remote: Boolean = false): Branches? {
            val git = repository.git
            val fs = repository.fileSystem
            val root = repository.root
            val (locals, remotes, worktrees) = try {
                Triple(git.branches(), if (remote) git.remoteBranches() else emptyList(), git.worktrees())
            } catch (e: GitException) {
                return null
            }
            val (here, top, sub, checkedOut) = layout(repository, worktrees) ?: return null

            // Branch name → ref: local first, then remote-tracking (origin before other remotes).
            val byName = linkedMapOf<String, String>()
            locals.forEach { byName[it] = it }
            remotes.sortedBy { if (it.startsWith("origin/")) 0 else 1 }.forEach { byName.getOrPut(it.substringAfter('/')) { it } }

            val mainBranch = repository.config.mainBranch
            if (mainBranch !in byName) return null

            val mainRef = byName.getValue(mainBranch)
            val mergedIntoMain = try { git.mergedInto(mainRef) } catch (e: GitException) { return null }
            val blobs = BlobCache(git)
            fun committed(ref: String) = Repository(root, GitTreeFileSystem(git, ref, top, blobs))
            val sources = linkedMapOf<String, Source>()
            val current = here.branch ?: "HEAD"
            sources[current] = Source(current, repository, lazy { committed(current) })
            for ((name, ref) in byName) {
                if (name in sources) continue
                val worktree = checkedOut[name]?.takeIf { ref == name }?.takeIf { fs.exists(it) }
                // A branch merged into mainBranch can't hold a real copy (rule 2) nor a ticket main lacks,
                // so it isn't read: old kept branches would cost a blob read per stale copy. Unless it's
                // checked out: a branch just started is "merged" but may have uncommitted edits.
                if (worktree == null && name != mainBranch && ref in mergedIntoMain) continue
                sources[name] = if (worktree != null) Source(ref, Repository(worktree, fs), lazy { committed(ref) }) else Source(ref, committed(ref))
            }
            val remoteRefs = remotes.groupBy { it.substringAfter('/') }
            return Branches(repository, git, sources, mainBranch, sub.joinToString("/"), remoteRefs).apply { merged[mainRef] = mergedIntoMain }
        }

        /**
         * Where things are: [here] is the worktree this checkout is in (the deepest one containing
         * the root), [top] its path, [sub] the repository root inside it, and [others] the
         * repository root in every other worktree by branch.
         */
        internal data class Layout(val here: Worktree, val top: Path, val sub: List<String>, val others: Map<String, Path>)

        internal fun layout(repository: Repository, worktrees: List<Worktree>): Layout? {
            val fs = repository.fileSystem
            val tops = worktrees.map { it to canonical(fs, it.path) }
            val (here, top) = tops.filter { (_, path) -> isInside(repository.root, path) }.maxByOrNull { (_, path) -> path.segments.size } ?: return null
            val sub = repository.root.relativeTo(top).segments.filter { it != "." } // relativeTo gives "." for the same path
            val others = tops.filter { (w, _) -> w.branch != null && w != here }.associate { (w, path) -> w.branch!! to sub.fold(path) { p, s -> p / s } }
            return Layout(here, top, sub, others)
        }

        private fun canonical(fs: FileSystem, path: Path): Path = if (fs.exists(path)) fs.canonicalize(path) else path

        private fun isInside(path: Path, dir: Path): Boolean =
            path.root == dir.root && path.segments.size >= dir.segments.size && path.segments.subList(0, dir.segments.size) == dir.segments
    }
}
