package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.testing.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextTest {
    private fun run(repo: GitRepo) = cli().test(listOf("--root", repo.root.toString(), "context"))

    @Test
    fun printsTheTicketOfATicketBranchOnly() {
        val repo = GitRepo.scenario("context")
        val onMain = run(repo)
        assertEquals(0, onMain.statusCode, onMain.output)
        assertEquals("", onMain.output, "not a ticket branch: nothing")

        repo.checkout("beta")
        val r = run(repo)
        assertEquals(0, r.statusCode, r.output)
        assertTrue("works on the Safanoria ticket `beta`" in r.stdout, r.stdout)
        assertTrue("<ticket file=\"" in r.stdout && "status: in-progress" in r.stdout, r.stdout)
        assertTrue("run `safanoria-cli finish beta` without being asked" in r.stdout, r.stdout)

        repo.checkout("feature")
        assertEquals("", run(repo).output, "a branch whose name has no ticket")
    }
}
