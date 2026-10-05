package dev.mateuy.safanoria.core

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigTest {
    private val path = "/repo/safanoria.yaml".toPath()

    @Test
    fun fullConfig() {
        val text = """
            safanoria: 1
            dir: issues
            mainBranch: master
            worktree: ../VacAppKMP--{id}
            components:
              app:
                version: { file: composeApp/gradle.properties, property: appVersionName }
              ktor:
                version: { file: server/build.gradle.kts, regex: 'version = "(.+)"' }
              rails:
                external: true    # released elsewhere
            channels: [whatsapp, email]
            tags:
              registry: Official registry integration
              offline: Working without a connection
            userRef: VacApp user id
            refs:
              sentry: { url: "https://example.sentry.io/issues/?query={id}" }
            learningTargets: [CLAUDE.md, docs/]
            unknownKey: kept
        """.trimIndent()
        val result = ConfigLoader.parse(path, text)
        assertTrue(result.diagnostics.isEmpty())
        val config = assertNotNull(result.config)
        assertEquals(1, config.specVersion)
        assertEquals(mapOf("registry" to "Official registry integration", "offline" to "Working without a connection"), config.tags)
        assertEquals("issues", config.dir)
        assertEquals("master", config.mainBranch)
        assertEquals("../VacAppKMP--{id}", config.worktree)
        assertEquals(VersionSource.Property("composeApp/gradle.properties", "appVersionName"), config.components["app"]?.version)
        assertEquals(VersionSource.Regex("server/build.gradle.kts", "version = \"(.+)\""), config.components["ktor"]?.version)
        assertEquals(true, config.components["rails"]?.external)
        assertNull(config.components["rails"]?.version)
        assertEquals(10, config.components["rails"]?.line)
        assertEquals(listOf("whatsapp", "email"), config.channels)
        assertEquals("https://example.sentry.io/issues/?query={id}", config.refs["sentry"]?.url)
        assertEquals(listOf("CLAUDE.md", "docs/"), config.learningTargets)
        assertEquals(20, config.keyLines["unknownKey"])
    }

    @Test
    fun defaults() {
        val config = assertNotNull(ConfigLoader.parse(path, "safanoria: 1\ncomponents:\n  app:\n    external: true\n").config)
        assertEquals("tickets", config.dir)
        assertEquals("main", config.mainBranch)
        assertNull(config.worktree)
        assertEquals(Config.DEFAULT_CHANNELS, config.channels)
    }

    @Test
    fun syntaxErrorHasLine() {
        val result = ConfigLoader.parse(path, "safanoria: 1\ncomponents: [unclosed\ndir: x\n")
        assertNull(result.config)
        val d = result.diagnostics.single()
        assertEquals("yaml-syntax", d.code)
        assertEquals(path, d.file)
        assertEquals(3, d.line)
        assertTrue(d.toString().startsWith("/repo/safanoria.yaml:3:"), d.toString())
    }

    @Test
    fun findRootWalksUp() {
        val fs = FakeFileSystem()
        fs.createDirectories("/repo/core/src".toPath())
        fs.write("/repo/safanoria.yaml".toPath()) { writeUtf8("safanoria: 1\n") }
        assertEquals("/repo".toPath(), ConfigLoader.findRoot(fs, "/repo/core/src".toPath()))
        assertEquals("/repo".toPath(), ConfigLoader.findRoot(fs, "/repo".toPath()))
        fs.createDirectories("/elsewhere".toPath())
        assertNull(ConfigLoader.findRoot(fs, "/elsewhere".toPath()))
    }

    @Test
    fun missingConfig() {
        val fs = FakeFileSystem()
        fs.createDirectories("/repo".toPath())
        val result = ConfigLoader.load(fs, "/repo".toPath())
        assertNull(result.config)
        assertEquals("config-missing", result.diagnostics.single().code)
    }
}
