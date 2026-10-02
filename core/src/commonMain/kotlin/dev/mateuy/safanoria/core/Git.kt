package dev.mateuy.safanoria.core

import okio.Path

public class ProcessResult(public val exitCode: Int, public val output: String)

/**
 * Runs a command line through the platform shell (`sh -c` / `cmd /c`) with stderr merged into
 * the output. Arguments pass through the shell: only use it with ids, paths and fixed options.
 * For arbitrary arguments it would need `posix_spawn`/`CreateProcess` (see native-spike).
 */
internal expect fun runCommand(commandLine: String): ProcessResult

public class GitException(message: String) : Exception(message)

/** The git calls Safanoria needs, run in [root]. */
public class Git(private val root: Path) {
    private fun git(vararg args: String): ProcessResult =
        runCommand((listOf("git", "-C", root.toString()) + args).joinToString(" ") { quote(it) } + " 2>&1")

    private fun gitOrThrow(vararg args: String): String {
        val result = git(*args)
        if (result.exitCode != 0) throw GitException("git ${args.joinToString(" ")} failed (${result.exitCode}): ${result.output.trim()}")
        return result.output
    }

    /** Whether a local or remote-tracking branch is called [name] (SPEC §3: new ids shouldn't clash). */
    public fun branchExists(name: String): Boolean =
        gitOrThrow("branch", "-a", "--list", name, "*/$name").lines().any { it.isNotBlank() }

    /** The current branch, or `HEAD` when detached. */
    public fun currentBranch(): String = gitOrThrow("rev-parse", "--abbrev-ref", "HEAD").trim()

    /** Paths of staged files, relative to the repository root. */
    public fun stagedFiles(): List<String> =
        gitOrThrow("diff", "--cached", "--name-only").lines().filter { it.isNotBlank() }

    private companion object {
        val SAFE = Regex("[A-Za-z0-9_./:=-]+")

        /** Double quotes work in both `sh` and `cmd.exe`; values never contain quotes. */
        fun quote(arg: String): String {
            require('"' !in arg) { "argument contains a quote: $arg" }
            return if (SAFE.matches(arg)) arg else "\"$arg\""
        }
    }
}
