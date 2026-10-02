package dev.mateuy.safanoria.core

import okio.FileSystem

/** The local file system (Okio's `FileSystem.SYSTEM`, which isn't in common code). */
public expect val SystemFileSystem: FileSystem

internal expect fun environment(name: String): String?

/** An environment variable, or null. */
public fun environmentVariable(name: String): String? = environment(name)

/** Sets the file's mode to 755 (owner rwx, others rx), as hooks need. No effect on Windows. */
internal expect fun makeExecutable(path: String)
