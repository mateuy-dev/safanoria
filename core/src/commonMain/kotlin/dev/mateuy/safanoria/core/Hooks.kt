package dev.mateuy.safanoria.core

import okio.Path

/** A `pre-commit` hook: none, Safanoria's, or someone else's (never overwritten). */
public enum class HookState { NONE, SAFANORIA, FOREIGN }

/** The git pre-commit hook that runs `safanoria validate --staged`. */
public object Hooks {
    public const val NAME: String = "pre-commit"
    private const val MARKER = "# safanoria pre-commit hook"

    /** The line to add to a project's own pre-commit hook (or hook manager). */
    public const val COMMAND: String = "safanoria validate --staged"

    /**
     * The hook. Without the CLI it lets the commit through with a warning: a teammate who hasn't
     * installed it isn't blocked, and CI validates anyway.
     */
    public val SCRIPT: String = """
        |#!/bin/sh
        |$MARKER (`safanoria hook install`; remove with `safanoria hook uninstall`)
        |if ! command -v safanoria >/dev/null 2>&1; then
        |  echo "safanoria is not installed: tickets not validated (see https://github.com/mateuy-dev/safanoria)" >&2
        |  exit 0
        |fi
        |exec $COMMAND
        |""".trimMargin()

    public fun path(git: Git): Path = git.hooksDir() / NAME

    public fun state(repository: Repository, path: Path): HookState {
        val fs = repository.fileSystem
        if (!fs.exists(path)) return HookState.NONE
        return if (MARKER in fs.read(path) { readUtf8() }) HookState.SAFANORIA else HookState.FOREIGN
    }

    /** Writes the hook at [path] and makes it executable. Callers check [state] first. */
    public fun install(repository: Repository, path: Path) {
        path.parent?.let { repository.fileSystem.createDirectories(it) }
        repository.fileSystem.write(path) { writeUtf8(SCRIPT) }
        makeExecutable(path.toString())
    }
}
