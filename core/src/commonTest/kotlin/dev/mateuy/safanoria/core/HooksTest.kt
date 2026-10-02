package dev.mateuy.safanoria.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HooksTest {
    /** A minimal git repository (HEAD, objects, refs), so no `git init` and no real hooks are touched. */
    private fun fakeRepository(name: String): Repository {
        val root = repoRoot / "core" / "build" / "test-repos" / name
        SystemFileSystem.deleteRecursively(root)
        SystemFileSystem.createDirectories(root / ".git" / "objects")
        SystemFileSystem.createDirectories(root / ".git" / "refs")
        SystemFileSystem.write(root / ".git" / "HEAD") { writeUtf8("ref: refs/heads/main\n") }
        SystemFileSystem.write(root / CONFIG_FILE) { writeUtf8("safanoria: 1\ncomponents:\n  app:\n    external: true\n") }
        return Repository(SystemFileSystem.canonicalize(root))
    }

    @Test
    fun installsAnExecutableHook() {
        val repo = fakeRepository("hooks")
        val path = Hooks.path(repo.git)
        // git prints C:/... on Windows; compare with one separator.
        assertEquals((repo.root / ".git" / "hooks" / "pre-commit").toString().replace('\\', '/'), path.toString().replace('\\', '/'))
        assertEquals(HookState.NONE, Hooks.state(repo, path))

        Hooks.install(repo, path)
        assertEquals(HookState.SAFANORIA, Hooks.state(repo, path))
        assertTrue(read(path).startsWith("#!/bin/sh\n# safanoria pre-commit hook") && read(path).endsWith("exec safanoria validate --staged\n"))
        if (environmentVariable("OS") != "Windows_NT") {
            assertEquals(0, runCommand("test -x \"$path\"").exitCode, "executable")
        }

        SystemFileSystem.write(path) { writeUtf8("#!/bin/sh\nnpx lint-staged\n") }
        assertEquals(HookState.FOREIGN, Hooks.state(repo, path))
    }
}
