package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.testing.test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AcrossBranchesTest {
    private fun run(repo: GitRepo, vararg args: String) = cli().test(listOf("--root", repo.root.toString()) + args.toList())

    @Test
    fun listShowsRealCopies() {
        val repo = GitRepo.scenario("across-list")
        val r = run(repo, "list")
        assertEquals(0, r.statusCode, r.output)
        val lines = r.stdout.lines().filter { it.isNotBlank() }
        assertEquals(listOf("beta", "alpha", "stray"), lines.map { it.substringBefore(' ') })
        assertTrue(lines[0].contains("in-progress"), lines[0])
        assertTrue(lines[2].endsWith("only on feature"), lines[2])

        val checkout = run(repo, "list", "--checkout")
        assertEquals(listOf("alpha", "beta"), checkout.stdout.lines().filter { it.isNotBlank() }.map { it.substringBefore(' ') })
        assertTrue(checkout.stdout.lines().all { "in-progress" !in it }, checkout.stdout)

        val json = Json.parseToJsonElement(run(repo, "list", "--format", "json").stdout).jsonObject.getValue("tickets").jsonArray
            .associateBy { it.jsonObject.getValue("id").jsonPrimitive.content }
        assertEquals("beta", json.getValue("beta").jsonObject.getValue("branch").jsonPrimitive.content)
        assertEquals(true, json.getValue("stray").jsonObject.getValue("onlyOnBranch").jsonPrimitive.boolean)
        assertEquals("tickets/stray.md", json.getValue("stray").jsonObject.getValue("file").jsonPrimitive.content)
    }

    @Test
    fun boardShowsRealCopies() {
        val repo = GitRepo.scenario("across-board")
        val r = run(repo, "board")
        assertEquals(0, r.statusCode, r.output)
        val inProgress = r.stdout.substringAfter("## In progress (1)\n\n").substringBefore("\n")
        assertEquals("- [beta](tickets/beta.md) Ticket beta", inProgress)
        assertTrue("- [stray](tickets/stray.md) Ticket stray · only on `feature`" in r.stdout, r.stdout)
    }

    @Test
    fun checkoutAndRemoteTogetherIsAUsageError() {
        val repo = GitRepo.scenario("across-usage")
        val r = run(repo, "list", "--checkout", "--remote")
        assertTrue(r.statusCode != 0)
        assertTrue("--checkout or --remote" in r.stderr, r.stderr)
    }
}
