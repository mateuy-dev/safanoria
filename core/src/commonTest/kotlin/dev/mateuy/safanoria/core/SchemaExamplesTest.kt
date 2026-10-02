package dev.mateuy.safanoria.core

import okio.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Runs every schema/examples file through the production validator: valid files have no errors,
 * invalid files fail with the `# expect: <keyword> <pointer>` on their first line.
 */
class SchemaExamplesTest {
    private fun check(schema: String, errorsOf: (YamlBlock) -> List<SchemaError>) {
        val dir = repoRoot / "schema" / "examples" / schema
        val problems = mutableListOf<String>()
        var count = 0
        for (file in SystemFileSystem.list(dir / "valid")) {
            count++
            errorsOf(block(file)).forEach { problems += "${file.name}: unexpected ${it.keyword} at ${it.pointer}: ${it.message}" }
        }
        for (file in SystemFileSystem.list(dir / "invalid")) {
            count++
            val expect = read(file).lineSequence().first().removePrefix("# expect: ").split(' ')
            val (keyword, pointer) = expect[0] to expect[1].let { if (it == "/") "" else it }
            val errors = errorsOf(block(file))
            if (errors.none { matches(it, keyword, pointer) }) {
                problems += "${file.name}: expected $keyword at ${expect[1]}, got " +
                    errors.joinToString { "${it.keyword} at ${it.pointer}" }.ifEmpty { "no errors" }
            }
        }
        assertTrue(count > 10, "examples not found in $dir")
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }

    /**
     * The examples' expectations come from Python's `jsonschema` (schema/check.py). OptimumCode
     * reports more detail for the same failure: the failing branches of an `anyOf`/`oneOf`
     * instead of the combinator, and the offending key's pointer for `propertyNames`. Both
     * locate the same problem, so accept an error at or under the expected pointer.
     */
    private fun matches(e: SchemaError, keyword: String, pointer: String): Boolean {
        val atOrUnder = e.pointer == pointer || e.pointer.startsWith("$pointer/")
        return atOrUnder && (e.keyword == keyword || keyword == "anyOf" || keyword == "oneOf")
    }

    private fun block(file: Path): YamlBlock =
        parseYamlBlock(file, read(file), firstLine = 1).first ?: fail("$file is not valid YAML")

    @Test
    fun ticketExamples() = check("ticket", SchemaValidator::ticketErrors)

    @Test
    fun configExamples() = check("safanoria", SchemaValidator::configErrors)

    @Test
    fun repositoryTicketsAndConfigAreValid() {
        for (root in listOf(repoRoot) + extraRepos) {
            assertEquals(emptyList(), ConfigLoader.load(SystemFileSystem, root).diagnostics.map { it.toString() }, "$root config")
            for (file in ticketFiles(root)) {
                val (frontmatter, diagnostics) = Frontmatter.parse(file, read(file))
                assertEquals(emptyList(), diagnostics.map { it.toString() })
                assertEquals(emptyList(), frontmatter!!.schemaDiagnostics().map { it.toString() })
            }
        }
    }
}
