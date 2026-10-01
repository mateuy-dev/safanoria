import com.charleskorn.kaml.Yaml
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlException
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import kotlinx.serialization.Serializable
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

expect val fileSystem: FileSystem

@Serializable
data class Frontmatter(
    val id: String,
    val type: String,
    val title: String,
    val status: String,
    val priority: String,
    val size: String,
    val created: String,
    val updated: String,
    val parent: String? = null,
    val blockedBy: List<String> = emptyList(),
    val related: List<String> = emptyList(),
)

private val yaml = Yaml(configuration = YamlConfiguration(strictMode = false))

/** Frontmatter text and the file line its first YAML line is on. */
fun frontmatter(text: String): Pair<String, Int>? {
    val lines = text.lines()
    if (lines.firstOrNull() != "---") return null
    val end = lines.drop(1).indexOf("---")
    if (end < 0) return null
    return lines.subList(1, end + 1).joinToString("\n") to 2
}

fun describe(node: YamlNode, lineOffset: Int): String = when (node) {
    is YamlMap -> node.entries.entries.joinToString(", ") { (k, v) ->
        "${k.content}@${k.location.line + lineOffset - 1}=${describe(v, lineOffset)}"
    }
    is YamlList -> node.items.joinToString(",", "[", "]") { describe(it, lineOffset) }
    is YamlScalar -> node.content
    else -> node.toString()
}

fun yamlCheck(root: Path) {
    val config = fileSystem.read(root / "safanoria.yaml") { readUtf8() }
    val configNode = yaml.parseToYamlNode(config)
    println("safanoria.yaml: ${describe(configNode, 1)}")

    val tickets = fileSystem.list(root / "tickets").filter { it.name.endsWith(".md") && !it.name.startsWith("_") }
    for (file in tickets) {
        val (fm, offset) = frontmatter(fileSystem.read(file) { readUtf8() }) ?: continue
        val node = yaml.parseToYamlNode(fm)
        val decoded = yaml.decodeFromString(Frontmatter.serializer(), fm)
        println("${file.name}: ${decoded.id} ${decoded.status} parent=${decoded.parent} blockedBy=${decoded.blockedBy}")
        println("  lines: ${describe(node, offset)}")
    }

    val broken = "id: x\ntype: [unclosed\nstatus: done\n"
    try {
        yaml.parseToYamlNode(broken)
    } catch (e: YamlException) {
        // kaml lines are 1-based; add the frontmatter offset - 1 for file lines.
        println("syntax error at line ${e.line} col ${e.column}: ${e.message}")
    }
    try {
        yaml.decodeFromString(Frontmatter.serializer(), "id: x\ntype: bug\n")
    } catch (e: Exception) {
        println("decode error: ${e::class.simpleName}: ${e.message}")
    }
}

class Safanoria : CliktCommand(name = "safanoria") {
    override fun run() = Unit
}

class YamlCmd : CliktCommand(name = "yaml") {
    private val root by argument().default(".")
    override fun run() = yamlCheck(root.toPath())
}

class Hello : CliktCommand(name = "hello") {
    private val path by argument()
    private val lines by option("--lines", "-n").int().default(3)
    override fun run() {
        val p = path.toPath()
        if (!fileSystem.exists(p)) throw PrintMessage("No such file: $p", statusCode = 2, printError = true)
        fileSystem.read(p) { generateSequence { readUtf8Line() }.take(lines).forEach { echo(it) } }
    }
}

class SchemaCmd : CliktCommand(name = "schema") {
    private val root by argument().default(".")
    override fun run() = schemaCheck(root.toPath())
}

class GitCmd : CliktCommand(name = "vcs") {
    private val id by argument()
    private val root by argument().default(".")
    override fun run() = gitCheck(root.toPath(), id)
}

/** What `validate` does before checking: config, ticket list, every frontmatter parsed. */
class Load : CliktCommand(name = "load") {
    private val root by argument().default(".")
    override fun run() {
        val r = root.toPath()
        yaml.parseToYamlNode(fileSystem.read(r / "safanoria.yaml") { readUtf8() })
        val nodes = fileSystem.list(r / "tickets").filter { it.name.endsWith(".md") }.mapNotNull { file ->
            frontmatter(fileSystem.read(file) { readUtf8() })?.let { yaml.parseToYamlNode(it.first) }
        }
        echo("${nodes.size} tickets")
    }
}

fun main(args: Array<String>) =Safanoria().subcommands(YamlCmd(), Hello(), SchemaCmd(), GitCmd(), Load()).main(args)
