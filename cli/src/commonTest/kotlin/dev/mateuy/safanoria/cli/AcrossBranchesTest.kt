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
    fun validateKnowsIdsOnOtherBranches() {
        val repo = GitRepo.scenario("across-validate")
        repo.write("tickets/late.md", GitRepo.ticket("late"))
        repo.commit("late on main")
        repo.checkout("feature")
        repo.write("tickets/gamma.md", GitRepo.ticket("gamma", related = listOf("late")))

        val r = run(repo, "validate")
        assertEquals(0, r.statusCode, r.output)
        assertEquals("ok: 4 tickets valid", r.stdout.trim())

        val checkout = run(repo, "validate", "--checkout")
        assertEquals(1, checkout.statusCode, checkout.output)
        assertTrue("error[ref-unknown]: related: no ticket 'late'" in checkout.stdout, checkout.stdout)
    }

    /** What actions/checkout does by default: one commit of one branch, and nothing of the others. */
    private fun ciCheckout(name: String, origin: GitRepo, branch: String): GitRepo {
        val ci = GitRepo(name)
        ci.git("remote", "add", "origin", origin.root.toString())
        ci.git("fetch", "-q", "--no-tags", "--depth=1", "origin", "+refs/heads/$branch:refs/remotes/origin/$branch")
        ci.git("checkout", "-q", "-B", branch, "refs/remotes/origin/$branch")
        return ci
    }

    @Test
    fun validateInACiCheckoutNeedsTheOtherBranches() {
        val repo = GitRepo.scenario("across-ci")
        repo.write("tickets/late.md", GitRepo.ticket("late"))
        repo.commit("late on main")
        repo.checkout("feature")
        repo.write("tickets/gamma.md", GitRepo.ticket("gamma", related = listOf("late")))
        repo.commit("gamma")

        val ci = ciCheckout("across-ci-runner", repo, "feature")
        val alone = run(ci, "validate")
        assertEquals(1, alone.statusCode, alone.output)
        assertTrue("error[ref-unknown]: related: no ticket 'late'" in alone.stdout, alone.stdout)
        assertTrue("note: this clone has no 'main' branch" in alone.stderr, alone.stderr)

        // What the Action does: the tip of every branch. No history joins them, so beta's two
        // copies (main's and its branch's) can't be told from an id created twice: not reported.
        ci.git("fetch", "-q", "--no-tags", "--depth=1", "origin", "+refs/heads/*:refs/remotes/origin/*")
        val fetched = run(ci, "validate")
        assertEquals(0, fetched.statusCode, fetched.output)
        assertEquals("ok: 4 tickets valid", fetched.stdout.trim())
    }

    @Test
    fun validateWarnsAboutAReferenceToAnUnpushedTicket() {
        val origin = GitRepo.scenario("across-unpushed-origin")
        origin.git("config", "receive.denyCurrentBranch", "ignore")
        val repo = GitRepo("across-unpushed")
        repo.git("remote", "add", "origin", origin.root.toString())
        repo.git("fetch", "-q", "origin")
        repo.git("checkout", "-q", "-B", "main", "origin/main")
        repo.write("tickets/late.md", GitRepo.ticket("late"))
        repo.commit("late on main, not pushed")
        repo.git("checkout", "-q", "-b", "gamma", "origin/main") // as after `new --on main` from a ticket's branch
        repo.write("tickets/gamma.md", GitRepo.ticket("gamma", related = listOf("late", "stray")))

        // stray is on origin/feature only: pushed. late is on local main only.
        val r = run(repo, "validate")
        assertEquals(0, r.statusCode, r.output)
        val warnings = r.stdout.lines().filter { "warning[" in it }
        assertEquals(1, warnings.size, r.stdout)
        assertTrue("warning[ref-unpushed]: related: 'late' is only on local branch 'main'" in warnings[0], r.stdout)

        repo.git("push", "-q", "origin", "main")
        val pushed = run(repo, "validate")
        assertEquals("ok: 3 tickets valid", pushed.stdout.trim(), pushed.output)
    }

    @Test
    fun validateWarnsAboutAnIdCreatedTwice() {
        val repo = GitRepo.scenario("across-twice")
        repo.checkout("other", create = true)
        repo.write("tickets/stray.md", GitRepo.ticket("stray", "ready").replace("Ticket stray", "Another stray"))
        repo.commit("same id, separately")

        val r = run(repo, "validate", "--staged")
        assertEquals(0, r.statusCode, r.output) // nothing staged
        val all = run(repo, "validate")
        assertEquals(0, all.statusCode, all.output) // a warning only
        assertTrue(
            // Paths are printed with the system's separator.
            "tickets/stray.md:2: warning[id-created-twice]: branch 'feature' also created a ticket 'stray' separately" in all.stdout.replace('\\', '/'),
            all.stdout,
        )
    }

    @Test
    fun checkoutAndRemoteTogetherIsAUsageError() {
        val repo = GitRepo.scenario("across-usage")
        val r = run(repo, "list", "--checkout", "--remote")
        assertTrue(r.statusCode != 0)
        assertTrue("--checkout or --remote" in r.stderr, r.stderr)
    }
}
