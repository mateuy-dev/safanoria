package dev.mateuy.safanoria.core

import okio.Path
import okio.Path.Companion.toPath

public class ProcessResult(public val exitCode: Int, public val output: String)

/**
 * Runs a command line through the platform shell (`sh -c` / `cmd /c`) with stderr merged into
 * the output. Arguments pass through the shell: only use it with ids, paths and fixed options.
 * For arbitrary arguments it would need `posix_spawn`/`CreateProcess` (see native-spike).
 */
internal expect fun runCommand(commandLine: String): ProcessResult

public class GitException(message: String) : Exception(message)

/** A checked-out working tree: [branch] is the short branch name, null when detached. */
public data class Worktree(val path: Path, val branch: String?)

/**
 * An entry of a tree object: [type] is `blob`, `tree` or `commit` (submodule); [id] its object
 * id; [size] in bytes for blobs.
 */
public data class TreeEntry(val mode: String, val type: String, val id: String, val size: Long?, val name: String)

/** The git calls Safanoria needs, run in [root]. */
public class Git(private val root: Path) {
    /** With [stderr] false, stderr is not captured (for output that must be exact, like file contents). */
    private fun git(vararg args: String, stderr: Boolean = true): ProcessResult =
        runCommand((listOf("git", "-C", root.toString()) + args).joinToString(" ") { quote(it) } + if (stderr) " 2>&1" else "")

    private fun gitOrThrow(vararg args: String): String {
        val result = git(*args)
        if (result.exitCode != 0) throw GitException("git ${args.joinToString(" ")} failed (${result.exitCode}): ${result.output.trim()}")
        return result.output
    }

    /** Runs any git command and returns its output (stderr included); throws [GitException] when it fails. */
    public fun run(vararg args: String): String = gitOrThrow(*args)

    /** Local branch names (`main`, `feature/x`). */
    public fun branches(): List<String> = refNames(gitOrThrow("for-each-ref", "--format=%(refname)", "refs/heads"))

    /** Remote-tracking branch names (`origin/main`), without the remotes' `HEAD`. */
    public fun remoteBranches(): List<String> = refNames(gitOrThrow("for-each-ref", "--format=%(refname)", "refs/remotes"))

    /** The branches (local and remote-tracking) whose tip is reachable from [target]. */
    public fun mergedInto(target: String): Set<String> =
        refNames(gitOrThrow("for-each-ref", "--merged", target, "--format=%(refname)", "refs/heads", "refs/remotes")).toSet()

    private fun refNames(output: String): List<String> = output.lines()
        .map { it.trim() }
        .mapNotNull { ref -> ref.removePrefixOrNull("refs/heads/") ?: ref.removePrefixOrNull("refs/remotes/") }
        .filter { !it.endsWith("/HEAD") }

    private fun String.removePrefixOrNull(prefix: String): String? = if (startsWith(prefix)) removePrefix(prefix) else null

    /** Every working tree of this repository, the main one first (`git worktree list`). */
    public fun worktrees(): List<Worktree> {
        val out = mutableListOf<Worktree>()
        var path: Path? = null
        var branch: String? = null
        for (line in gitOrThrow("worktree", "list", "--porcelain").lines() + "") {
            when {
                line.startsWith("worktree ") -> { path = line.removePrefix("worktree ").toPath(); branch = null }
                line.startsWith("branch ") -> branch = line.removePrefix("branch ").removePrefix("refs/heads/")
                line.isBlank() -> { path?.let { out += Worktree(it, branch) }; path = null }
            }
        }
        return out
    }

    /** The commit id [ref] points to, or null if there is no such commit. */
    public fun commitId(ref: String): String? =
        git("rev-parse", "--verify", "--quiet", "$ref^{commit}").takeIf { it.exitCode == 0 }?.output?.trim()

    /**
     * The entries of directory [dir] (relative to the root, `""` for the root) in commit [ref],
     * or null when [ref] doesn't have that directory.
     */
    public fun tree(ref: String, dir: String): List<TreeEntry>? {
        // --full-tree: without it, ls-tree run from a subdirectory (a project below the git top level) lists only that part.
        val result = git("-c", "core.quotePath=false", "ls-tree", "--full-tree", "-l", "$ref:$dir", stderr = false)
        if (result.exitCode != 0) return null
        return result.output.lines().filter { it.isNotBlank() }.map { line ->
            val (meta, name) = line.split('\t', limit = 2)
            val (mode, type, id, size) = meta.trim().split(Regex(" +"))
            TreeEntry(mode, type, id, size.toLongOrNull(), name)
        }
    }

    /** The content of blob [id], as UTF-8 text. */
    public fun blob(id: String): String {
        val result = git("cat-file", "-p", id, stderr = false)
        if (result.exitCode != 0) throw GitException("git cat-file -p $id failed (${result.exitCode})")
        return result.output
    }

    /** Whether commit [ref] has [path] (relative to the root). */
    public fun hasPath(ref: String, path: String): Boolean = git("cat-file", "-e", "$ref:$path").exitCode == 0

    /** The common git directory (shared by all worktrees). Absolute. */
    public fun commonDir(): Path = gitOrThrow("rev-parse", "--path-format=absolute", "--git-common-dir").trim().toPath()

    /** Stores [file] as a blob, exactly as it is (no line-ending filters), and returns its id. */
    public fun hashObject(file: Path): String = gitOrThrow("hash-object", "-w", "--no-filters", file.toString()).trim()

    /**
     * Stores a tree with [entries] and returns its id. `mktree` reads stdin, which [runCommand]
     * can't feed, so the entries go through [scratch], a file redirected in (`<` works in `sh` and `cmd`).
     */
    public fun mktree(entries: List<TreeEntry>, scratch: Path, fileSystem: okio.FileSystem): String {
        fileSystem.write(scratch) { entries.forEach { writeUtf8("${it.mode} ${it.type} ${it.id}\t${it.name}\n") } }
        val result = runCommand("git -C ${quote(root.toString())} mktree < ${quote(scratch.toString())} 2>&1")
        fileSystem.delete(scratch)
        if (result.exitCode != 0) throw GitException("git mktree failed (${result.exitCode}): ${result.output.trim()}")
        return result.output.trim()
    }

    /** Creates a commit of [tree] on top of [parent] and returns its id; the author is the user's git identity. */
    public fun commitTree(tree: String, parent: String, message: String): String =
        gitOrThrow("commit-tree", tree, "-p", parent, "-m", message).trim()

    /** Moves branch [branch] from [old] to [new]; fails if it moved meanwhile. */
    public fun updateBranch(branch: String, new: String, old: String) {
        gitOrThrow("update-ref", "-m", "safanoria-cli new", "refs/heads/$branch", new, old)
    }

    /** The best common ancestor of [a] and [b], or null when they share no history. */
    public fun mergeBase(a: String, b: String): String? =
        git("merge-base", a, b).takeIf { it.exitCode == 0 }?.output?.trim()

    /** Whether a local or remote-tracking branch is called [name] (SPEC §3: new ids shouldn't clash). */
    public fun branchExists(name: String): Boolean =
        gitOrThrow("branch", "-a", "--list", name, "*/$name").lines().any { it.isNotBlank() }

    /** The current branch, or `HEAD` when detached. */
    public fun currentBranch(): String = gitOrThrow("rev-parse", "--abbrev-ref", "HEAD").trim()

    /**
     * The directory git runs hooks from: `core.hooksPath` if set, else the common `.git/hooks`
     * (shared by all worktrees). Absolute.
     */
    public fun hooksDir(): Path {
        val path = gitOrThrow("rev-parse", "--path-format=absolute", "--git-path", "hooks").trim()
        return path.toPath()
    }

    /** Paths of staged files, relative to the repository root. */
    public fun stagedFiles(): List<String> =
        gitOrThrow("diff", "--cached", "--name-only").lines().filter { it.isNotBlank() }
}

private val SAFE = Regex("[A-Za-z0-9_./:=-]+")

/** Quotes one [runCommand] argument. Double quotes work in both `sh` and `cmd.exe`; values never contain quotes. */
internal fun quote(arg: String): String {
    require('"' !in arg) { "argument contains a quote: $arg" }
    return if (SAFE.matches(arg)) arg else "\"$arg\""
}
