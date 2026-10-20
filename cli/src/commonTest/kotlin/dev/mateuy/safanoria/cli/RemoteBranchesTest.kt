package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.testing.test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A ticket started on another computer, seen from a clone that only has it as `origin/<id>` (SPEC §14.1). */
class RemoteBranchesTest {
    private fun run(repo: GitRepo, vararg args: String) = cli().test(listOf("--root", repo.root.toString()) + args.toList())

    /** A clone of [GitRepo.scenario] with only main checked out: beta, in progress, is on origin/beta only. */
    private fun clone(name: String): GitRepo {
        val origin = GitRepo.scenario("$name-origin")
        val repo = GitRepo(name)
        repo.git("remote", "add", "origin", origin.root.toString())
        repo.git("fetch", "-q", "origin")
        repo.git("checkout", "-q", "-B", "main", "origin/main")
        return repo
    }

    @Test
    fun listShowsTheRemoteStatusMarkedUnlessLocal() {
        val repo = clone("remote-list")
        val r = run(repo, "list")
        assertEquals(0, r.statusCode, r.output)
        val beta = r.stdout.lines().first { it.startsWith("beta ") }
        assertTrue("in-progress" in beta && beta.endsWith("on origin/beta"), beta)
        assertTrue(r.stdout.lines().first { it.startsWith("stray ") }.endsWith("only on origin/feature"), r.stdout)

        val json = Json.parseToJsonElement(run(repo, "list", "--format", "json").stdout).jsonObject.getValue("tickets").jsonArray
            .associateBy { it.jsonObject.getValue("id").jsonPrimitive.content }
        assertEquals("origin", json.getValue("beta").jsonObject.getValue("remote").jsonPrimitive.content)

        val local = run(repo, "list", "--local")
        assertTrue("in-progress" !in local.stdout.lines().first { it.startsWith("beta ") }, local.stdout)

        assertTrue("· on `origin/beta`" in run(repo, "board").stdout)
    }

    @Test
    fun startRefusesAndSaysHowToCheckItOut() {
        val repo = clone("remote-start")
        val r = run(repo, "start", "beta")
        assertEquals(1, r.statusCode, r.output)
        assertTrue("started on another computer: it is on origin/beta" in r.stderr, r.stderr)
        val command = r.stderr.lines().first { it.startsWith("Check it out with: ") }.removePrefix("Check it out with: ")
        assertTrue(command.startsWith("git worktree add --track -b beta ") && command.endsWith(" origin/beta"), command)
        assertTrue(repo.git("branch", "--list", "beta").isBlank())
    }

    @Test
    fun finishRefusesWithoutALocalBranch() {
        val repo = clone("remote-finish")
        val r = run(repo, "finish", "beta")
        assertEquals(1, r.statusCode, r.output)
        assertTrue("being worked on another computer: it is on origin/beta" in r.stderr, r.stderr)
    }
}
