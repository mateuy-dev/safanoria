package dev.mateuy.safanoria.core

import okio.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * One mini repository per SPEC §12 rule under fixtures/validate/<case>/: the validator must
 * report exactly the `<file>:<line> <code>` lines of its expected.txt. With an only.txt, only
 * those files are given (pre-commit mode).
 */
class ValidatorFixturesTest {
    private val fixtures = repoRoot / "core" / "src" / "commonTest" / "fixtures" / "validate"

    private fun lines(path: Path): List<String> =
        if (SystemFileSystem.exists(path)) read(path).lines().filter { it.isNotBlank() } else emptyList()

    @Test
    fun everyFixture() {
        val cases = SystemFileSystem.list(fixtures).filter { SystemFileSystem.metadata(it).isDirectory }
        assertTrue(cases.size >= 30, "fixtures not found in $fixtures")
        val problems = mutableListOf<String>()
        for (case in cases) {
            val repository = Repository(case)
            val only = lines(case / "only.txt").map { case / it }.ifEmpty { null }
            val actual = Validator(repository).validate(only).map { d ->
                "${d.file!!.relativeTo(case).toString().replace('\\', '/')}:${d.line} ${d.code}"
            }.sorted()
            val expected = lines(case / "expected.txt").sorted()
            if (actual != expected) {
                val all = Validator(repository).validate(only)
                problems += "${case.name}:\n  expected ${expected}\n  actual   $actual\n  " + all.joinToString("\n  ")
            }
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }
}
