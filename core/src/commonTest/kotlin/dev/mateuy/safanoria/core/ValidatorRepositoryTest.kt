package dev.mateuy.safanoria.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ValidatorRepositoryTest {
    @Test
    fun thisRepositoryIsValid() {
        assertEquals(emptyList(), Validator(Repository(repoRoot)).validate().map { it.toString() })
    }

    @Test
    fun extraRepositoriesAreReported() {
        for (root in extraRepos) {
            val diagnostics = Validator(Repository(root)).validate()
            // Other projects may break the format; report, don't fail.
            println("$root: ${diagnostics.size} problems")
            diagnostics.groupingBy { it.code }.eachCount().forEach { (code, n) -> println("  $code: $n") }
        }
    }
}
