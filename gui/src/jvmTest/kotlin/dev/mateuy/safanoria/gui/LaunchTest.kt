package dev.mateuy.safanoria.gui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LaunchTest {
    private fun launch(vararg args: String) = launch(args.toList(), "1.2.3") { it == "some/dir" }

    @Test
    fun opensTheWorkingDirectoryOrTheGivenOne() {
        assertEquals(Launch.Open(null), launch())
        assertEquals(Launch.Open("some/dir"), launch("some/dir"))
    }

    @Test
    fun versionAndHelp() {
        assertEquals(Launch.Message("safanoria 1.2.3 (spec 1)", 0), launch("--version"))
        val help = assertIs<Launch.Message>(launch("--help"))
        assertTrue(help.status == 0 && "safanoria-cli" in help.text, help.text)
    }

    /** What hooks and scripts written for the old `safanoria` (the CLI) get: no window, and the new command. */
    @Test
    fun cliCommandsPointToSafanoriaCli() {
        for (args in listOf(listOf("validate", "--staged"), listOf("context"), listOf("--root", "some/dir", "list"), listOf("some/dir", "extra"))) {
            val message = assertIs<Launch.Message>(launch(*args.toTypedArray()))
            assertEquals(2, message.status)
            assertTrue("  safanoria-cli ${args.joinToString(" ")}\n" in message.text, message.text)
        }
    }
}
