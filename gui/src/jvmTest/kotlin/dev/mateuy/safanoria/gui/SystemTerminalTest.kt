package dev.mateuy.safanoria.gui

import dev.mateuy.safanoria.gui.data.SystemTerminal
import okio.Path.Companion.toPath
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SystemTerminalTest {
    /** A PATH directory holding executables with these names. */
    private fun pathWith(vararg commands: String): String {
        val dir = Files.createTempDirectory("terminals").toFile().apply { deleteOnExit() }
        commands.forEach { File(dir, it).apply { writeText("#!/bin/sh\n"); setExecutable(true); deleteOnExit() } }
        return dir.path
    }

    private fun linux(path: String, terminal: String? = null) =
        SystemTerminal("Linux") { name -> mapOf("PATH" to path, "TERMINAL" to terminal)[name] }

    @Test
    fun linuxUsesTheFirstTerminalOnThePath() {
        assertEquals(listOf("kitty"), linux(pathWith("xterm", "kitty")).command("/work"))
    }

    @Test
    fun terminalsRunByAServerAreToldTheDirectory() {
        assertEquals(listOf("gnome-terminal", "--working-directory=/work"), linux(pathWith("gnome-terminal")).command("/work"))
        assertEquals(listOf("konsole", "--workdir", "/work"), linux(pathWith("konsole")).command("/work"))
    }

    @Test
    fun theTerminalVariableWinsWhenItCanBeRun() {
        val path = pathWith("xterm", "kitty")
        assertEquals(listOf("xterm"), linux(path, terminal = "xterm").command("/work"))
        assertEquals(listOf("kitty"), linux(path, terminal = "not-installed").command("/work"))
    }

    @Test
    fun noTerminalIsAnAnswerNotACrash() {
        val terminal = linux(pathWith())
        assertNull(terminal.command("/work"))
        assertNotNull(terminal.open(System.getProperty("java.io.tmpdir").toPath()))
    }

    @Test
    fun aMissingDirectoryIsReported() {
        assertEquals("no such directory: /no/such/dir", linux(pathWith("xterm")).open("/no/such/dir".toPath()))
    }

    @Test
    fun macAndWindowsUseTheirOwn() {
        assertEquals(listOf("open", "-a", "Terminal", "/work"), SystemTerminal("Mac OS X") { null }.command("/work"))
        assertEquals(listOf("cmd", "/c", "start", "cmd"), SystemTerminal("Windows 11") { null }.command("C:\\work"))
    }
}
