package dev.mateuy.safanoria.core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.toKString
import okio.FileSystem
import platform.posix.getenv

public actual val SystemFileSystem: FileSystem = FileSystem.SYSTEM

@OptIn(ExperimentalForeignApi::class)
internal actual fun environment(name: String): String? = getenv(name)?.toKString()

@OptIn(ExperimentalForeignApi::class)
internal actual fun makeExecutable(path: String) {
    platform.posix.chmod(path, 493.convert()) // 0755; Windows ignores the execute bits
}
