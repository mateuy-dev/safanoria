package dev.mateuy.safanoria.core

internal actual fun runCommand(commandLine: String): ProcessResult {
    val windows = System.getProperty("os.name").startsWith("Windows")
    val shell = if (windows) listOf("cmd", "/c", commandLine) else listOf("sh", "-c", commandLine)
    val process = ProcessBuilder(shell).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    return ProcessResult(process.waitFor(), output)
}
