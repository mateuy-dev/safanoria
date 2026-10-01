package dev.mateuy.safanoria.core

import okio.Path
import okio.Path.Companion.toPath

/** This repository, set by the Gradle test tasks. */
val repoRoot: Path = (environment("SAFANORIA_REPO_ROOT") ?: error("SAFANORIA_REPO_ROOT not set: run tests through Gradle")).toPath()

/**
 * Other local repositories whose tickets the tests also read (never copied here: they may be
 * private). Empty unless SAFANORIA_EXTRA_REPOS is set.
 */
val extraRepos: List<Path> = environment("SAFANORIA_EXTRA_REPOS")
    ?.split(':', ';')?.filter { it.isNotBlank() }?.map { it.toPath() } ?: emptyList()

/** Ticket files of [root], read with its own `safanoria.yaml` `dir`. */
fun ticketFiles(root: Path): List<Path> {
    val dir = ConfigLoader.load(SystemFileSystem, root).config?.dir ?: "tickets"
    return SystemFileSystem.list(root / dir).filter { it.name.endsWith(".md") && !it.name.startsWith("_") && it.name != "README.md" }
}

fun read(path: Path): String = SystemFileSystem.read(path) { readUtf8() }
