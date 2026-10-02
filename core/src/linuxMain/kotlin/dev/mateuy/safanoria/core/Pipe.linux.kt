package dev.mateuy.safanoria.core

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.FILE
import platform.posix.pclose
import platform.posix.popen

@OptIn(ExperimentalForeignApi::class)
internal actual fun openPipe(commandLine: String): CPointer<FILE>? = popen(commandLine, "r")

// pclose returns a wait status: the exit code is in bits 8-15 (WEXITSTATUS).
@OptIn(ExperimentalForeignApi::class)
internal actual fun closePipe(pipe: CPointer<FILE>): Int = (pclose(pipe) shr 8) and 0xff
