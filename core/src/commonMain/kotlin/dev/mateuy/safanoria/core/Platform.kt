package dev.mateuy.safanoria.core

import okio.FileSystem

/** The local file system (Okio's `FileSystem.SYSTEM`, which isn't in common code). */
public expect val SystemFileSystem: FileSystem

internal expect fun environment(name: String): String?
