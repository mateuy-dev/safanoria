package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.testing.test
import dev.mateuy.safanoria.core.SystemFileSystem
import dev.mateuy.safanoria.core.environmentVariable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidateTest {
    private val repoRoot = (environmentVariable("SAFANORIA_REPO_ROOT") ?: error("run through Gradle")).toPath()
    private val fixtures = repoRoot / "core" / "src" / "commonTest" / "fixtures" / "validate"

    private fun run(vararg args: String) = cli().test(args.toList())

    @Test
    fun validRepository() {
        val r = run("--root", (fixtures / "valid").toString(), "validate")
        assertEquals(0, r.statusCode, r.output)
        assertTrue(r.stdout.contains("ok: 6 tickets valid"), r.stdout)
    }

    @Test
    fun problemsExitOneWithFileAndLine() {
        val r = run("--root", (fixtures / "ref-unknown").toString(), "validate")
        assertEquals(1, r.statusCode)
        val lines = r.stdout.lines().filter { it.contains("error[") }
        assertEquals(5, lines.size, r.stdout)
        assertTrue(lines.all { Regex(".*refs\\.md:\\d+(:\\d+)?: error\\[ref-unknown]: .+").matches(it) }, r.stdout)
        assertTrue(r.stderr.contains("5 problems in 1 file"), r.stderr)
    }

    @Test
    fun jsonFormat() {
        val r = run("--root", (fixtures / "child-check-mismatch").toString(), "validate", "--format", "json")
        assertEquals(1, r.statusCode)
        val json = Json.parseToJsonElement(r.stdout).jsonObject
        assertEquals(false, json["valid"]!!.jsonPrimitive.boolean)
        val codes = json["diagnostics"]!!.jsonArray.map { it.jsonObject["code"]!!.jsonPrimitive.content }
        assertEquals(listOf("child-check-mismatch", "child-check-mismatch"), codes)
        assertTrue(json["diagnostics"]!!.jsonArray.all { it.jsonObject["line"]!!.jsonPrimitive.content.toInt() > 0 })
    }

    @Test
    fun onlyGivenFilesReportsWhatTheyCause() {
        val case = fixtures / "only-given-files"
        val child = run("--root", case.toString(), "validate", (case / "tickets" / "child-one.md").toString())
        assertEquals(1, child.statusCode)
        assertTrue(child.stdout.contains("parent-one.md"), child.stdout)       // caused by the child
        assertTrue(!child.stdout.contains("unrelated.md"), child.stdout)       // not given, not caused
        val unrelated = run("--root", case.toString(), "validate", (case / "tickets" / "unrelated.md").toString())
        assertTrue(unrelated.stdout.contains("schema-enum") && !unrelated.stdout.contains("parent-one"), unrelated.stdout)
    }

    @Test
    fun thisRepositoryIsValid() {
        val r = run("--root", repoRoot.toString(), "validate")
        assertEquals(0, r.statusCode, r.output)
    }

    @Test
    fun usageErrorsExitTwo() {
        val missing = run("--root", repoRoot.toString(), "validate", "no-such-file.md")
        assertEquals(2, missing.statusCode)
        // Above the repository there is no safanoria.yaml (checked, in case a parent has one).
        val above = SystemFileSystem.canonicalize(repoRoot).parent!!
        if (dev.mateuy.safanoria.core.ConfigLoader.findRoot(SystemFileSystem, above) == null) {
            val notRepo = run("--root", above.toString(), "validate")
            assertEquals(2, notRepo.statusCode, notRepo.output)
        }
        val both = run("--root", repoRoot.toString(), "validate", "--staged", "x.md")
        assertEquals(2, both.statusCode)
    }
}
