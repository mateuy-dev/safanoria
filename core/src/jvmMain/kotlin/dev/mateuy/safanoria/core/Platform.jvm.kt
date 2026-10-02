package dev.mateuy.safanoria.core

import okio.FileSystem

public actual val SystemFileSystem: FileSystem = FileSystem.SYSTEM

internal actual fun environment(name: String): String? = System.getenv(name)

internal actual fun makeExecutable(path: String) {
    java.io.File(path).setExecutable(true, false)
}
