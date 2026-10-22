package dev.mateuy.safanoria.core

import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HerdrTest {
    private val dir = "/work/proj--alpha".toPath()

    private fun pane(id: String, tab: String, workspace: String, cwd: String) =
        """{"pane_id":"$id","tab_id":"$tab","workspace_id":"$workspace","cwd":"$cwd","foreground_cwd":"$cwd"}"""

    private fun herdr(panes: List<String>, tabCounts: Map<String, Int>, log: MutableList<String> = mutableListOf(), here: String? = null) = Herdr(here) { line ->
        log += line
        val body = when {
            "pane list" in line -> """{"panes":[${panes.joinToString(",")}]}"""
            "workspace list" in line -> """{"workspaces":[${tabCounts.entries.joinToString(",") { """{"workspace_id":"${it.key}","tab_count":${it.value}}""" }}]}"""
            "create" in line -> """{"root_pane":{"pane_id":"w9:p1"}}"""
            else -> "{}"
        }
        ProcessResult(0, """{"id":"x","result":$body}""")
    }

    @Test
    fun agentNamesFollowHerdrsRules() {
        assertEquals("alpha-1", Herdr.agentName("alpha.1"))
        assertEquals("t-2fa", Herdr.agentName("2fa"))
        assertEquals(32, Herdr.agentName("a".repeat(50)).length)
    }

    @Test
    fun opensATabOrAWorkspaceAndStartsClaude() {
        val log = mutableListOf<String>()
        val r = herdr(emptyList(), emptyMap(), log).open(dir, "alpha", "Start working on ticket alpha", workspace = true)
        assertEquals("opened a workspace 'alpha' running Claude Code", r)
        assertEquals("herdr workspace create --cwd /work/proj--alpha --label alpha --no-focus 2>&1", log[0])
        assertEquals(
            "herdr agent start alpha --kind claude --pane w9:p1 -- --name alpha \"Start working on ticket alpha\" 2>&1",
            log[1],
        )
    }

    @Test
    fun claudeCanStartWithoutAPrompt() {
        val log = mutableListOf<String>()
        val r = herdr(emptyList(), emptyMap(), log).open(dir, "alpha", prompt = null, workspace = true, claude = true)
        assertEquals("opened a workspace 'alpha' running Claude Code", r)
        assertEquals("herdr agent start alpha --kind claude --pane w9:p1 -- --name alpha 2>&1", log[1])
    }

    @Test
    fun withoutClaudeOnlyAShellIsLeft() {
        val log = mutableListOf<String>()
        val r = herdr(emptyList(), emptyMap(), log).open(dir, "alpha", prompt = null, workspace = true)
        assertEquals("opened a workspace 'alpha'", r)
        assertEquals(listOf("herdr workspace create --cwd /work/proj--alpha --label alpha --no-focus 2>&1"), log)
    }

    @Test
    fun aWorkspaceOnlyOfThisWorktreeClosesWhole() {
        val h = herdr(listOf(pane("w2:p1", "w2:t1", "w2", "/work/proj--alpha"), pane("w1:p1", "w1:t1", "w1", "/work/proj")), mapOf("w1" to 1, "w2" to 1))
        assertEquals(listOf(HerdrTarget(HerdrTarget.Kind.WORKSPACE, "w2", false)), h.closing(dir))
    }

    @Test
    fun aSharedWorkspaceLosesOnlyTheTab() {
        val h = herdr(listOf(pane("w1:p2", "w1:t2", "w1", "/work/proj--alpha/sub"), pane("w1:p1", "w1:t1", "w1", "/work/proj")), mapOf("w1" to 2))
        assertEquals(listOf(HerdrTarget(HerdrTarget.Kind.TAB, "w1:t2", false)), h.closing(dir))
    }

    @Test
    fun theCurrentOneComesLast() {
        val panes = listOf(pane("w1:p1", "w1:t1", "w1", "/work/proj--alpha"), pane("w2:p1", "w2:t1", "w2", "/work/proj--alpha"))
        val h = herdr(panes, mapOf("w1" to 1, "w2" to 1), here = "w1:p1")
        assertEquals(listOf("w2" to false, "w1" to true), h.closing(dir).map { it.id to it.current })
    }

    @Test
    fun aSiblingDirectoryIsNotThisWorktree() {
        val h = herdr(listOf(pane("w1:p1", "w1:t1", "w1", "/work/proj--alpha-2")), mapOf("w1" to 1))
        assertEquals(emptyList(), h.closing(dir))
    }

    @Test
    fun failuresSayWhatHerdrReturned() {
        val h = Herdr(null) { ProcessResult(1, "boom") }
        val e = assertFailsWith<HerdrException> { h.closing(dir) }
        assertEquals("herdr pane list failed (1): boom", e.message)
    }
}
