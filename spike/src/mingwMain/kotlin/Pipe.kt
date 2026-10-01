import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.FILE
import platform.posix._pclose
import platform.posix._popen

@OptIn(ExperimentalForeignApi::class)
actual fun openPipe(commandLine: String): CPointer<FILE>? = _popen(commandLine, "r")

// _pclose returns the exit code directly.
@OptIn(ExperimentalForeignApi::class)
actual fun closePipe(pipe: CPointer<FILE>): Int = _pclose(pipe)
