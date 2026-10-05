package dev.mateuy.safanoria.core

import okio.Path

/**
 * A `pre-commit` hook: none, Safanoria's, Safanoria's from another version (e.g. one that calls the
 * CLI by its old name, `safanoria`), or someone else's (never overwritten).
 */
public enum class HookState { NONE, SAFANORIA, OUTDATED, FOREIGN }

/** The git pre-commit hook that runs `safanoria-cli validate --staged`. */
public object Hooks {
    public const val NAME: String = "pre-commit"
    private const val MARKER = "# safanoria pre-commit hook"

    /** The line to add to a project's own pre-commit hook (or hook manager). */
    public const val COMMAND: String = "safanoria-cli validate --staged"

    /**
     * The hook. Without the CLI it lets the commit through with a warning: a teammate who hasn't
     * installed it isn't blocked, and CI validates anyway.
     */
    public val SCRIPT: String = """
        |#!/bin/sh
        |$MARKER (`safanoria-cli hook install`; remove with `safanoria-cli hook uninstall`)
        |if ! command -v safanoria-cli >/dev/null 2>&1; then
        |  echo "safanoria-cli is not installed: tickets not validated (see https://github.com/mateuy-dev/safanoria)" >&2
        |  exit 0
        |fi
        |exec $COMMAND
        |""".trimMargin()

    public fun path(git: Git): Path = git.hooksDir() / NAME

    public fun state(repository: Repository, path: Path): HookState {
        val fs = repository.fileSystem
        if (!fs.exists(path)) return HookState.NONE
        val text = fs.read(path) { readUtf8() }.replace("\r\n", "\n")
        return when {
            text == SCRIPT -> HookState.SAFANORIA
            MARKER in text -> HookState.OUTDATED
            else -> HookState.FOREIGN
        }
    }

    /** Writes the hook at [path] and makes it executable. Callers check [state] first. */
    public fun install(repository: Repository, path: Path) {
        path.parent?.let { repository.fileSystem.createDirectories(it) }
        repository.fileSystem.write(path) { writeUtf8(SCRIPT) }
        makeExecutable(path.toString())
    }
}
