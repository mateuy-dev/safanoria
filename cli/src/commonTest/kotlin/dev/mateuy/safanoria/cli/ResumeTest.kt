package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.testing.test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResumeTest {
    private fun run(repo: GitRepo, vararg args: String) = cli().test(listOf("--root", repo.root.toString()) + args.toList())

    /** The across-branches scenario, plus gamma: in progress in a worktree with an uncommitted file. */
    private fun scenario(name: String): Pair<GitRepo, okio.Path> {
        val repo = GitRepo.scenario(name)
        repo.git("branch", "gamma")
        val wt = repo.worktree("gamma")
        repo.write("tickets/gamma.md", GitRepo.ticket("gamma", "in-progress"), at = wt)
        repo.commit("gamma started", at = wt)
        repo.write("scratch.txt", "wip", at = wt)
        return repo to wt
    }

    @Test
    fun textSaysWhereAndHowToGetThere() {
        val (repo, wt) = scenario("resume-text")
        val r = run(repo, "resume")
        assertEquals(0, r.statusCode, r.output)
        val blocks = r.stdout.trim().split("\n\n")
        assertEquals(listOf("beta", "gamma"), blocks.map { it.substringBefore(' ') })
        assertTrue("  branch    beta\n  worktree  none\n" in blocks[0], blocks[0])
        assertTrue("  go there  git switch beta" in blocks[0], blocks[0])
        assertTrue("  worktree  $wt  (1 uncommitted file)" in blocks[1], blocks[1])
        assertTrue("  next      [ ] Step" in blocks[1], blocks[1])
        assertTrue("  last log  2026-10-02 · status · started" in blocks[1], blocks[1])
        assertTrue("  go there  cd $wt" in blocks[1], blocks[1])
    }

    @Test
    fun wordsAndIds() {
        val (repo, _) = scenario("resume-words")
        assertEquals("gamma", run(repo, "resume", "ticket", "GAM").stdout.substringBefore(' '))
        assertEquals("alpha", run(repo, "resume", "alpha").stdout.substringBefore(' '))
        assertTrue("go there  not started: no branch alpha" in run(repo, "resume", "alpha").stdout)

        val none = run(repo, "resume", "nothing")
        assertEquals(1, none.statusCode)
        assertEquals("No ticket in progress or review matches 'nothing'.", none.stderr.trim())
    }

    @Test
    fun withAWorktreeSettingItSaysHowToCreateIt() {
        val (repo, _) = scenario("resume-setting")
        repo.write("safanoria.yaml", GitRepo.CONFIG + "worktree: ../app--{id}\n")
        assertTrue("go there  git worktree add ../app--beta beta" in run(repo, "resume", "beta").stdout)
    }

    @Test
    fun jsonForAgents() {
        val (repo, wt) = scenario("resume-json")
        val tickets = Json.parseToJsonElement(run(repo, "resume", "--format", "json").stdout).jsonObject.getValue("tickets").jsonArray
            .associateBy { it.jsonObject.getValue("id").jsonPrimitive.content }
        val gamma = tickets.getValue("gamma").jsonObject
        assertEquals(wt.toString(), gamma.getValue("worktree").jsonPrimitive.content)
        assertEquals(1, gamma.getValue("uncommitted").jsonPrimitive.int)
        assertEquals("cd $wt", gamma.getValue("command").jsonPrimitive.content)
        assertEquals(false, gamma.getValue("here").jsonPrimitive.boolean)

        val fromThere = cli().test(listOf("--root", wt.toString(), "resume", "gamma", "--format", "json"))
        val here = Json.parseToJsonElement(fromThere.stdout).jsonObject.getValue("tickets").jsonArray.single().jsonObject
        assertEquals(true, here.getValue("here").jsonPrimitive.boolean)
        assertEquals("null", here.getValue("command").toString())
    }
}
