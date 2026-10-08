package dev.mateuy.safanoria.core

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstallTest {
    private val root = "/project".toPath()
    private val fs = FakeFileSystem().apply { createDirectories(root) }

    private fun plan(dir: String = "tickets") = Install.plan(fs, root, dir)
    private fun actions(dir: String = "tickets") =
        plan(dir).associate { it.path.relativeTo(root).segments.joinToString("/") to it.action }
    private fun write(changes: List<FileChange>) = changes.forEach { c ->
        c.path.parent?.let { fs.createDirectories(it) }
        fs.write(c.path) { writeUtf8(c.text) }
    }

    @Test
    fun marker() {
        val skill = Install.managedFiles().getValue("SKILL.md")
        assertTrue(skill.startsWith(Embedded.SKILL.trimEnd('\n')) && skill.endsWith("\n\n<!-- safanoria ${Embedded.VERSION} -->\n"), skill.takeLast(80))
        assertEquals(Embedded.VERSION, Install.installedVersion(skill))
        assertNull(Install.installedVersion(Embedded.SKILL), "the source has no marker")
        assertEquals("0.9.0", Install.installedVersion("x\n<!-- safanoria 0.8.0 -->\ny\n<!-- safanoria 0.9.0 -->\n"), "the last one")
    }

    @Test
    fun emptyProject() {
        assertEquals(
            mapOf(
                ".claude/skills/safanoria/SKILL.md" to FileAction.CREATE,
                ".claude/skills/safanoria/SPEC.md" to FileAction.CREATE,
                "issues/_TEMPLATE.md" to FileAction.CREATE,
                "issues/_TEMPLATE.bug.md" to FileAction.CREATE,
                "issues/_TEMPLATE.research.md" to FileAction.CREATE,
                "issues/README.md" to FileAction.CREATE,
                "CLAUDE.md" to FileAction.CREATE,
                ".claude/settings.json" to FileAction.CREATE,
            ),
            actions("issues"),
        )
        write(plan("issues"))
        assertTrue(actions("issues").values.all { it == FileAction.SAME }, actions("issues").toString())
        assertTrue(read("issues/_TEMPLATE.bug.md").contains("### Steps to reproduce"))
        assertTrue(read("CLAUDE.md").contains("`issues/<id>.md`"))
    }

    @Test
    fun installedByHand() {
        // Like VacAppKMP before `update`: unmarked copies, its own template and README, CLAUDE.md with the paragraph.
        fs.createDirectories(root / ".claude/skills/safanoria".toPath())
        fs.createDirectories(root / "tickets")
        fs.write(root / ".claude/skills/safanoria/SKILL.md".toPath()) { writeUtf8("old skill\n") }
        fs.write(root / "tickets" / "_TEMPLATE.md") { writeUtf8(Embedded.TICKET_TEMPLATE.replace("What we want and why.", "Our own text.")) }
        fs.write(root / "tickets" / "README.md") { writeUtf8("# Our tickets\n") }
        fs.write(root / "CLAUDE.md") { writeUtf8("# Project\n\nUse the `safanoria` skill for tickets.\n") }

        assertNull(Install.installedVersion(read(".claude/skills/safanoria/SKILL.md")))
        assertEquals(
            mapOf(
                ".claude/skills/safanoria/SKILL.md" to FileAction.REPLACE,
                ".claude/skills/safanoria/SPEC.md" to FileAction.CREATE,
                "tickets/_TEMPLATE.md" to FileAction.DIFFERS,
                "tickets/_TEMPLATE.bug.md" to FileAction.CREATE,
                "tickets/_TEMPLATE.research.md" to FileAction.CREATE,
                "tickets/README.md" to FileAction.SAME,
                "CLAUDE.md" to FileAction.SAME,
                ".claude/settings.json" to FileAction.CREATE,
            ),
            actions(),
        )
    }

    @Test
    fun sessionStartHook() {
        val created = Install.settingsWithHook(null)!!
        assertTrue(Install.CONTEXT_HOOK in created, created)
        assertEquals(created, Install.settingsWithHook(created), "already there: unchanged")

        // Existing settings and hooks are kept; the hook is added to SessionStart.
        val existing = """{"permissions": {"allow": ["Bash(ls)"]}, "hooks": {"SessionStart": [{"hooks": [{"type": "command", "command": "echo hi"}]}], "Stop": []}}"""
        val merged = Install.settingsWithHook(existing)!!
        for (kept in listOf("Bash(ls)", "echo hi", "\"Stop\"", Install.CONTEXT_HOOK)) assertTrue(kept in merged, merged)
        assertTrue(merged.indexOf("echo hi") < merged.indexOf(Install.CONTEXT_HOOK), merged)

        // The CLI's old name (`safanoria`, now the desktop app) is renamed in place.
        assertEquals(created, Install.settingsWithHook(created.replace("safanoria-cli context", "safanoria context")))
        assertNull(Install.settingsWithHook("not json"))
        assertNull(Install.settingsWithHook("""{"hooks": []}"""), "a shape we don't know is left alone")

        fs.createDirectories(root / ".claude")
        fs.write(root / Install.SETTINGS_FILE) { writeUtf8(existing) }
        assertEquals(FileAction.DIFFERS, actions()[Install.SETTINGS_FILE], "existing settings change only after asking")
        fs.write(root / Install.SETTINGS_FILE) { writeUtf8("not json") }
        assertEquals(FileAction.SAME, actions()[Install.SETTINGS_FILE])
    }

    @Test
    fun claudeMdWithoutTheParagraph() {
        fs.write(root / "CLAUDE.md") { writeUtf8("# Project\n\nBuild with gradle.\n") }
        val change = plan().single { it.path.name == "CLAUDE.md" }
        assertEquals(FileAction.DIFFERS, change.action, "an existing CLAUDE.md is changed only after asking")
        assertEquals("# Project\n\nBuild with gradle.\n\n" + Install.claudeParagraph("tickets"), change.text)
    }

    @Test
    fun configValidates() {
        val text = Install.config("tickets", listOf(
            ComponentSpec("app", VersionSource.Property("composeApp/gradle.properties", "appVersionName")),
            ComponentSpec("rails", null),
        ), worktree = "../project--{id}")
        val result = ConfigLoader.parse(root / CONFIG_FILE, text)
        assertEquals(emptyList(), result.diagnostics)
        val config = result.config!!
        assertEquals("../project--{id}", config.worktree)
        assertEquals(listOf("app", "rails"), config.components.keys.toList())
        assertEquals(VersionSource.Property("composeApp/gradle.properties", "appVersionName"), config.components.getValue("app").version)
        assertTrue(config.components.getValue("rails").external)
    }

    private fun read(path: String) = fs.read(root / path.toPath()) { readUtf8() }
}
