import okio.Path

class ProcessResult(val exitCode: Int, val output: String)

/** Runs a command line through the platform shell; stdout and stderr are merged. */
expect fun runCommand(commandLine: String): ProcessResult

fun git(root: Path, vararg args: String): ProcessResult =
    runCommand((listOf("git", "-C", root.toString()) + args).joinToString(" ") { quote(it) } + " 2>&1")

/** Quoting for sh and cmd.exe alike: double quotes; ids and paths never contain quotes. */
private fun quote(arg: String) = if (arg.all { it.isLetterOrDigit() || it in "-_./:=" }) arg else "\"$arg\""

fun gitCheck(root: Path, id: String) {
    val branch = git(root, "branch", "--list", id)
    println("branch --list $id: exit ${branch.exitCode}, exists=${branch.output.isNotBlank()}")
    val missing = git(root, "branch", "--list", "no-such-branch")
    println("branch --list no-such-branch: exit ${missing.exitCode}, exists=${missing.output.isNotBlank()}")
    val staged = git(root, "diff", "--cached", "--name-only")
    println("staged: exit ${staged.exitCode}: ${staged.output.lines().filter { it.isNotBlank() }}")
    val bad = git(root, "no-such-command")
    println("bad command: exit ${bad.exitCode}: ${bad.output.lineSequence().first()}")
}
