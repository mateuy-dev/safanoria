import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.posix.FILE
import platform.posix.fgets

@OptIn(ExperimentalForeignApi::class)
actual fun runCommand(commandLine: String): ProcessResult {
    val pipe = openPipe(commandLine) ?: return ProcessResult(-1, "popen failed")
    val out = StringBuilder()
    memScoped {
        val buffer = allocArray<ByteVar>(4096)
        while (fgets(buffer, 4096, pipe) != null) out.append(buffer.toKString())
    }
    return ProcessResult(closePipe(pipe), out.toString())
}

/** `popen` on POSIX, `_popen` on Windows. */
@OptIn(ExperimentalForeignApi::class)
expect fun openPipe(commandLine: String): CPointer<FILE>?

/** Closes the pipe and returns the command's exit code. */
@OptIn(ExperimentalForeignApi::class)
expect fun closePipe(pipe: CPointer<FILE>): Int
