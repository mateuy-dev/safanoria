package dev.mateuy.safanoria.core

import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class SessionAgentTest {
    @Test
    fun projectDefaultAndExplicitPrecedence() {
        val base = "safanoria: 1\nworktree: ../p--{id}\ncomponents:\n  app:\n    external: true\n"
        fun config(extra: String) = ConfigLoader.parse("/p/safanoria.yaml".toPath(), base + extra)
        assertEquals(SessionAgent.CLAUDE, config("").config!!.defaultAgent)
        val codex = config("defaultAgent: codex\n")
        assertTrue(codex.diagnostics.isEmpty())
        assertEquals(SessionAgent.CODEX, codex.config!!.defaultAgent)
        assertEquals(SessionAgent.CODEX, SessionAgent.resolve(null, codex.config.defaultAgent))
        assertEquals(SessionAgent.CLAUDE, SessionAgent.resolve(SessionAgent.CLAUDE, codex.config.defaultAgent))
        assertEquals(SessionAgent.CLAUDE, SessionAgent.resolve(null, null))
        for (invalid in listOf("other", "123", "[codex]"))
            assertTrue(config("defaultAgent: $invalid\n").diagnostics.isNotEmpty(), invalid)
    }

    @Test
    fun bothAgentsAndEveryPromptMode() {
        for (agent in SessionAgent.entries) for (mode in SessionPrompt.entries) {
            val calls = mutableListOf<String>()
            val herdr = Herdr(null) { command ->
                calls += command
                ProcessResult(0, if (calls.size == 1) """{"result":{"root_pane":{"pane_id":"w9:p1"}}}""" else """{"result":{}}""")
            }
            val result = herdr.open("/work/space dir".toPath(), "ticket-name", mode.text("ticket-name"), true, agent)
            assertTrue(agent.label in result)
            assertTrue("--kind ${agent.option}" in calls[1])
            assertEquals(mode == SessionPrompt.NONE, !calls[1].contains("Implement ticket") && !calls[1].contains("Let's discuss"))
            assertEquals(agent == SessionAgent.CLAUDE, "--name ticket-name" in calls[1])
            if (agent == SessionAgent.CODEX) assertTrue("project_doc_fallback_filenames" in calls[1])
            assertTrue("--cwd \"/work/space dir\"" in calls[0])
        }
    }

    @Test
    fun startupFailureNamesTheAgentAndKeepsTheCreatedPane() {
        for (agent in SessionAgent.entries) {
            var calls = 0
            val h = Herdr(null) {
                calls++
                if (calls == 1) ProcessResult(0, """{"result":{"root_pane":{"pane_id":"w9:p1"}}}""")
                else ProcessResult(1, "executable missing")
            }
            val error = assertFailsWith<HerdrException> { h.open("/work".toPath(), "alpha", null, true, agent) }
            assertTrue("${agent.label} didn't start" in error.message.orEmpty())
            assertTrue("pane w9:p1" in error.message.orEmpty())
            assertTrue("executable missing" in error.message.orEmpty())
            assertEquals(2, calls)
        }
    }

    @Test
    fun shellQuotesPromptsWithoutExpandingTheirContents() {
        if (environmentVariable("OS") == "Windows_NT") return
        val value = "quotes \" ' dollar ${'$'}HOME backtick `uname` newline\nbackslash \\"
        val result = runCommand("printf '%s' " + launchQuote(value))
        assertEquals(0, result.exitCode)
        assertEquals(value, result.output)
    }
    @Test
    fun tabsUseTheCallerWorkspaceForBothAgents() {
        for (agent in SessionAgent.entries) {
            val calls = mutableListOf<String>()
            val h = Herdr(currentWorkspace = "w7") { line ->
                calls += line
                ProcessResult(0, """{"result":{"root_pane":{"pane_id":"w7:p9"}}}""")
            }
            assertTrue("opened a tab" in h.open("/work".toPath(), "alpha", null, false, agent))
            assertEquals("herdr tab create --workspace w7 --cwd /work --label alpha --no-focus 2>&1", calls[0])
            assertTrue("--kind ${agent.option} --pane w7:p9" in calls[1])
        }
    }

}
