package dev.mateuy.safanoria.core

import okio.Path

/**
 * A real git repository under `core/build/test-repos/<name>`, recreated on every use, with
 * `main` as the first branch, a fixed committer and no line ending conversion, so tests don't
 * depend on the user's config.
 *
 * Each test uses its own [name]: on Windows a repository with commits can't be deleted from a
 * test (git's object files are read-only), so Gradle clears the directory before each test task.
 */
class GitFixture(name: String, base: Path = repoRoot / "core" / "build" / "test-repos") {
    val root: Path

    init {
        val dir = base / name
        SystemFileSystem.deleteRecursively(dir)
        SystemFileSystem.createDirectories(dir)
        root = SystemFileSystem.canonicalize(dir)
        git("init", "-q", "-b", "main")
    }

    val repository: Repository get() = Repository(root)

    /** Runs git in [at] (default: [root]) and returns its output; fails the test on errors. */
    fun git(vararg args: String, at: Path = root): String {
        val line = (listOf("git", "-C", at.toString(), "-c", "user.name=test", "-c", "user.email=test@example.com", "-c", "commit.gpgsign=false", "-c", "core.autocrlf=false") + args)
            .joinToString(" ") { quote(it) }
        val result = runCommand("$line 2>&1")
        check(result.exitCode == 0) { "$line failed (${result.exitCode}): ${result.output}" }
        return result.output
    }

    fun write(relative: String, text: String, at: Path = root) {
        val path = at / relative
        path.parent?.let { SystemFileSystem.createDirectories(it) }
        SystemFileSystem.write(path) { writeUtf8(text) }
    }

    fun delete(relative: String, at: Path = root) = SystemFileSystem.delete(at / relative)

    /** Stages everything and commits. */
    fun commit(message: String, at: Path = root) {
        git("add", "-A", at = at)
        git("commit", "-q", "--no-verify", "-m", message, at = at)
    }

    fun checkout(branch: String, create: Boolean = false, at: Path = root) {
        if (create) git("checkout", "-q", "-b", branch, at = at) else git("checkout", "-q", branch, at = at)
    }

    /** Adds a worktree for [branch] (created from the current HEAD when [create]) and returns its path. */
    fun worktree(branch: String, create: Boolean = false): Path {
        val path = root.parent!! / "${root.name}--$branch"
        SystemFileSystem.deleteRecursively(path)
        if (create) git("worktree", "add", "-q", "-b", branch, path.toString()) else git("worktree", "add", "-q", path.toString(), branch)
        return SystemFileSystem.canonicalize(path)
    }

    fun merge(branch: String, at: Path = root) = git("merge", "-q", "--no-ff", "--no-edit", branch, at = at)

    companion object {
        const val CONFIG: String = "safanoria: 1\ncomponents:\n  app:\n    external: true\n"

        /** A minimal valid ticket. */
        fun ticket(id: String, status: String = "backlog", parent: String? = null, related: List<String> = emptyList(), extra: String = ""): String = buildString {
            append("---\nid: $id\ntype: feature\ntitle: Ticket $id\nstatus: $status\npriority: medium\nsize: S\n")
            append("created: 2026-10-02\nupdated: 2026-10-02\n")
            if (parent != null) append("parent: $parent\n")
            if (related.isNotEmpty()) append("related: [${related.joinToString()}]\n")
            append("---\n\n## Objective\n\nDo $id.\n$extra\n## Acceptance Criteria\n\n- [ ] Done\n\n## Plan\n\n- [ ] Step\n\n## Work Log\n\n- **2026-10-02** · status · started\n")
        }
    }
}
