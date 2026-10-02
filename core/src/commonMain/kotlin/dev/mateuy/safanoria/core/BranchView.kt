package dev.mateuy.safanoria.core

import okio.Buffer
import okio.FileMetadata
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Source

/**
 * One branch as a [Repository], to create tickets on it without checking it out (SPEC §14.2).
 * When the branch is checked out in a worktree (often `main` in the main checkout), [repository]
 * is that worktree and [commit] writes there and commits only the given files. Otherwise
 * [repository] reads the branch's last commit and [commit] adds a commit with git plumbing, so
 * no working tree or index changes.
 */
public class BranchView private constructor(
    public val branch: String,
    public val repository: Repository,
    /** The repository root inside the worktree where [branch] is checked out, or null. */
    public val worktree: Path?,
    private val git: Git,
    /** The repository root relative to the git top level, as tree path segments. */
    private val sub: List<String>,
) {
    /** [files] as they would be after writing them: to validate before committing anything. */
    public fun withFiles(files: List<PlannedFile>): Repository =
        Repository(repository.root, OverlayFileSystem(repository.fileSystem, files.associate { it.path.normalized() to it.text }))

    /** Writes [files] (paths under [repository]'s root) on [branch] in one commit; returns the commit id. */
    public fun commit(files: List<PlannedFile>, message: String): String {
        val relative = files.map { it.path.relativeTo(repository.root).segments.joinToString("/") }
        if (worktree != null) {
            files.forEach { f -> SystemFileSystem.write(f.path) { writeUtf8(f.text) } }
            val wt = Git(worktree)
            wt.run("add", "--", *relative.toTypedArray())
            // `commit -- <paths>` commits only these, leaving whatever else the user has staged.
            wt.run("commit", "-q", "-m", message, "--", *relative.toTypedArray())
            return wt.commitId("HEAD") ?: throw GitException("no commit after git commit")
        }

        val old = git.commitId("refs/heads/$branch") ?: throw GitException("no branch '$branch'")
        val scratch = git.commonDir() / "safanoria-new.tmp"
        val fs = SystemFileSystem
        // Changes per directory (tree path), applied bottom-up: blobs first, then each new tree in its parent.
        val changes = mutableMapOf<String, MutableMap<String, TreeEntry>>()
        for ((f, rel) in files.zip(relative)) {
            fs.write(scratch) { writeUtf8(f.text) }
            val blob = git.hashObject(scratch)
            val path = (sub + rel.split('/')).joinToString("/")
            changes.getOrPut(path.substringBeforeLast('/', "")) { mutableMapOf() }[path.substringAfterLast('/')] =
                TreeEntry("100644", "blob", blob, null, path.substringAfterLast('/'))
        }
        fs.delete(scratch)
        val dirs = changes.keys.flatMap { dir -> generateSequence(dir) { d -> if (d.isEmpty()) null else d.substringBeforeLast('/', "") }.toList() }.toSet()
        var tree = ""
        for (dir in dirs.sortedByDescending { if (it.isEmpty()) 0 else it.count { c -> c == '/' } + 1 }) {
            val entries = (git.tree(old, dir).orEmpty().associateBy { it.name } + changes[dir].orEmpty()).values.sortedBy { it.name }
            tree = git.mktree(entries, scratch, fs)
            if (dir.isNotEmpty()) {
                val name = dir.substringAfterLast('/')
                changes.getOrPut(dir.substringBeforeLast('/', "")) { mutableMapOf() }[name] = TreeEntry("040000", "tree", tree, null, name)
            }
        }
        val commit = git.commitTree(tree, old, message)
        git.updateBranch(branch, commit, old)
        return commit
    }

    public companion object {
        /** [branch] of [repository]'s git repository, or null when there is no such local branch. */
        public fun open(repository: Repository, branch: String): BranchView? {
            val git = repository.git
            val layout = try {
                if (git.commitId("refs/heads/$branch") == null) return null
                Branches.layout(repository, git.worktrees())
            } catch (e: GitException) {
                return null
            } ?: return null
            val worktree = if (layout.here.branch == branch) repository.root else layout.others[branch]?.takeIf { SystemFileSystem.exists(it) }
            val view = if (worktree != null) Repository(worktree, repository.fileSystem)
                else Repository(repository.root, GitTreeFileSystem(git, "refs/heads/$branch", layout.top))
            return BranchView(branch, view, worktree, git, layout.sub)
        }
    }
}

/** [base] with [files] added or replaced, read-only: what a repository would look like after writing them. */
internal class OverlayFileSystem(base: FileSystem, private val files: Map<Path, String>) : ForwardingFileSystem(base) {
    override fun metadataOrNull(path: Path): FileMetadata? =
        if (path.normalized() in files) FileMetadata(isRegularFile = true, size = files.getValue(path.normalized()).encodeToByteArray().size.toLong())
        else super.metadataOrNull(path)

    override fun source(file: Path): Source = files[file.normalized()]?.let { Buffer().writeUtf8(it) } ?: super.source(file)

    override fun listOrNull(dir: Path): List<Path>? {
        val added = files.keys.filter { it.parent == dir.normalized() }
        val listed = super.listOrNull(dir) ?: if (added.isEmpty()) return null else emptyList()
        return (listed + added).distinctBy { it.normalized() }.sortedBy { it.name }
    }

    override fun list(dir: Path): List<Path> = listOrNull(dir) ?: super.list(dir)

    override fun canonicalize(path: Path): Path = if (path.normalized() in files) path.normalized() else super.canonicalize(path)
}
