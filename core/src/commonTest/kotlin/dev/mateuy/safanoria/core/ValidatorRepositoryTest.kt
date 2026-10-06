package dev.mateuy.safanoria.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ValidatorRepositoryTest {
    @Test
    fun thisRepositoryIsValid() {
        // Like `safanoria-cli validate`: ids on other branches are known (SPEC §12), e.g. a ticket
        // created on main while working on a ticket branch. Remote-tracking branches too: CI has
        // no local main. ref-unpushed is about this clone (main not pushed yet), not the tickets.
        val repository = Repository(repoRoot)
        val diagnostics = Validator(repository, Branches.read(repository, remote = true)).validate().filter { it.code != "ref-unpushed" }
        assertEquals(emptyList(), diagnostics.map { it.toString() })
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
