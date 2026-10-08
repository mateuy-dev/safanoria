package dev.mateuy.safanoria.cli

import dev.mateuy.safanoria.core.Git
import dev.mateuy.safanoria.core.SystemFileSystem
import dev.mateuy.safanoria.core.environmentVariable
import okio.Path
import okio.Path.Companion.toPath

/**
 * A real git repository under `cli/build/test-repos/<name>`, recreated on every use, with `main`
 * as the first branch and a fixed committer. The core tests have the full scenarios
 * (`BranchesTest`); this one is for the commands' wiring.
 *
 * Each test uses its own [name]: on Windows a repository with commits can't be deleted from a
 * test (git's object files are read-only), so Gradle clears the directory before each test task.
 */
class GitRepo(name: String) {
    val root: Path

    init {
        val repoRoot = (environmentVariable("SAFANORIA_REPO_ROOT") ?: error("run through Gradle")).toPath()
        val dir = repoRoot / "cli" / "build" / "test-repos" / name
        SystemFileSystem.deleteRecursively(dir)
        SystemFileSystem.createDirectories(dir)
        root = SystemFileSystem.canonicalize(dir)
        git("init", "-q", "-b", "main")
        // In the repository's config, not -c: the commands under test commit too (new --on).
        git("config", "user.name", "test")
        git("config", "user.email", "test@example.com")
        git("config", "commit.gpgsign", "false")
        git("config", "core.autocrlf", "false") // Git for Windows checks files out with CRLF
    }

    fun git(vararg args: String, at: Path = root): String = Git(at).run(*args)

    fun write(relative: String, text: String, at: Path = root) {
        val path = at / relative
        path.parent?.let { SystemFileSystem.createDirectories(it) }
        SystemFileSystem.write(path) { writeUtf8(text) }
    }

    fun read(relative: String, at: Path = root): String = SystemFileSystem.read(at / relative) { readUtf8() }

    fun commit(message: String, at: Path = root) {
        git("add", "-A", at = at)
        git("commit", "-q", "--no-verify", "-m", message, at = at)
    }

    fun checkout(branch: String, create: Boolean = false) {
        if (create) git("checkout", "-q", "-b", branch) else git("checkout", "-q", branch)
    }

    /** Adds a worktree for an existing [branch] next to the repository and returns its path. */
    fun worktree(branch: String): Path {
        val path = root.parent!! / "${root.name}--$branch"
        SystemFileSystem.deleteRecursively(path)
        git("worktree", "add", "-q", path.toString(), branch)
        return SystemFileSystem.canonicalize(path)
    }

    companion object {
        const val CONFIG: String = "safanoria: 1\ncomponents:\n  app:\n    external: true\n"

        /** [CONFIG] with ticket worktrees next to repository [name], where [worktree] adds them too. */
        fun config(name: String): String = CONFIG + "worktree: ../$name--{id}\n"

        fun ticket(id: String, status: String = "backlog", related: List<String> = emptyList()): String = buildString {
            append("---\nid: $id\ntype: feature\ntitle: Ticket $id\nstatus: $status\npriority: medium\nsize: S\n")
            append("created: 2026-10-02\nupdated: 2026-10-02\n")
            if (related.isNotEmpty()) append("related: [${related.joinToString()}]\n")
            append("---\n\n## Objective\n\nDo $id.\n\n## Acceptance Criteria\n\n- [ ] Done\n\n## Plan\n\n- [ ] Step\n\n## Work Log\n\n- **2026-10-02** · status · started\n")
        }

        /** main: alpha, beta; beta started on its branch; stray only on feature. */
        fun scenario(name: String): GitRepo {
            val r = GitRepo(name)
            r.write("safanoria.yaml", config(name))
            r.write("tickets/alpha.md", ticket("alpha"))
            r.write("tickets/beta.md", ticket("beta"))
            r.commit("tickets")
            r.checkout("beta", create = true)
            r.write("tickets/beta.md", ticket("beta", "in-progress"))
            r.commit("beta started")
            r.checkout("main")
            r.checkout("feature", create = true)
            r.write("tickets/stray.md", ticket("stray"))
            r.commit("stray")
            r.checkout("main")
            return r
        }
    }
}
