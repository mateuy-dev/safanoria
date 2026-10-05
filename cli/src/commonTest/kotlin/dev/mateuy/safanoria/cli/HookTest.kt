package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.testing.test
import dev.mateuy.safanoria.core.SystemFileSystem
import dev.mateuy.safanoria.core.environmentVariable
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HookTest {
    private val repoRoot = (environmentVariable("SAFANORIA_REPO_ROOT") ?: error("run through Gradle")).toPath()
    private val work: Path = repoRoot / "cli" / "build" / "test-repos" / "hook"
    private val hook get() = work / ".git" / "hooks" / "pre-commit"

    /** A minimal git repository (HEAD, objects, refs): no `git init`, and this repository's hooks are never touched. */
    @BeforeTest
    fun fakeRepository() {
        SystemFileSystem.deleteRecursively(work)
        SystemFileSystem.createDirectories(work / ".git" / "objects")
        SystemFileSystem.createDirectories(work / ".git" / "refs")
        SystemFileSystem.write(work / ".git" / "HEAD") { writeUtf8("ref: refs/heads/main\n") }
        SystemFileSystem.write(work / "safanoria.yaml") { writeUtf8("safanoria: 1\ncomponents:\n  app:\n    external: true\n") }
    }

    private fun run(vararg args: String) = cli().test(listOf("--root", work.toString(), "hook") + args.toList())
    private fun read(path: Path) = SystemFileSystem.read(path) { readUtf8() }

    @Test
    fun installAndUninstall() {
        val dry = run("install", "--dry-run")
        assertEquals(0, dry.statusCode, dry.output)
        assertTrue(dry.stdout.startsWith("would install"), dry.stdout)
        assertFalse(SystemFileSystem.exists(hook))

        val r = run("install")
        assertEquals(0, r.statusCode, r.output)
        assertTrue(r.stdout.contains("runs `safanoria-cli validate --staged` before each commit"), r.stdout)
        assertTrue(read(hook).contains("exec safanoria-cli validate --staged"))
        assertTrue(run("install").stdout.startsWith("already installed"))

        val removed = run("uninstall")
        assertEquals(0, removed.statusCode, removed.output)
        assertFalse(SystemFileSystem.exists(hook))
        assertEquals("not installed\n", run("uninstall").stdout)
    }

    /** A hook from 0.2 or earlier calls the CLI `safanoria`, which is now the desktop app. */
    @Test
    fun updatesAHookThatCallsTheOldCommand() {
        run("install")
        val old = read(hook).replace("safanoria-cli", "safanoria")
        SystemFileSystem.write(hook) { writeUtf8(old) }
        val r = run("install")
        assertEquals(0, r.statusCode, r.output)
        assertTrue(r.stdout.startsWith("updated"), r.stdout)
        assertTrue(read(hook).contains("command -v safanoria-cli") && read(hook).endsWith("exec safanoria-cli validate --staged\n"))
        assertTrue(run("install").stdout.startsWith("already installed"))
    }

    @Test
    fun leavesAnotherToolsHookAlone() {
        SystemFileSystem.createDirectories(hook.parent!!)
        SystemFileSystem.write(hook) { writeUtf8("#!/bin/sh\nnpx lint-staged\n") }
        val r = run("install")
        assertEquals(1, r.statusCode)
        assertTrue(r.stderr.contains("another tool's hook") && r.stderr.contains("  safanoria-cli validate --staged"), r.stderr)
        assertEquals(1, run("uninstall").statusCode)
        assertEquals("#!/bin/sh\nnpx lint-staged\n", read(hook))
    }
}
