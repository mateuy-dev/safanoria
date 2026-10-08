package dev.mateuy.safanoria.core

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersionsTest {
    private val fs = FakeFileSystem().apply {
        createDirectories("/repo/composeApp".toPath())
        write("/repo/safanoria.yaml".toPath()) { writeUtf8("safanoria: 1\nworktree: ../project--{id}\ncomponents:\n  app:\n    external: true\n") }
        write("/repo/composeApp/gradle.properties".toPath()) {
            writeUtf8("# appVersionName=0.0.1\nkotlin.code.style=official\nappVersionName = 4.3.0\nappVersionCode=430\n")
        }
        write("/repo/package.json".toPath()) { writeUtf8("{\n  \"name\": \"web\",\n  \"version\": \"2.8.0\"\n}\n") }
    }
    private val repo = Repository("/repo".toPath(), fs)

    private fun read(source: VersionSource) = Versions.read(repo, source)

    @Test
    fun parse() {
        assertEquals(Version(4, 3, 10), Version.parse("4.3.10"))
        for (bad in listOf("4.3", "v4.3.0", "4.3.0-dev", "04.3.0", "4.3.0 ", "99999999999.0.0")) assertNull(Version.parse(bad), bad)
        assertTrue(Version(4, 10, 0) > Version(4, 9, 9))
        assertEquals("4.3.0", Version(4, 3, 0).toString())
    }

    @Test
    fun propertySource() {
        assertEquals(VersionRead.Found(Version(4, 3, 0)), read(VersionSource.Property("composeApp/gradle.properties", "appVersionName")))
        assertEquals(
            VersionRead.Failed("'430' in composeApp/gradle.properties is not MAJOR.MINOR.PATCH"),
            read(VersionSource.Property("composeApp/gradle.properties", "appVersionCode")),
        )
        assertEquals(VersionRead.Failed("no 'missing=' line in composeApp/gradle.properties"), read(VersionSource.Property("composeApp/gradle.properties", "missing")))
        assertEquals(VersionRead.Failed("nope.properties not found"), read(VersionSource.Property("nope.properties", "v")))
    }

    @Test
    fun regexSource() {
        assertEquals(VersionRead.Found(Version(2, 8, 0)), read(VersionSource.Regex("package.json", """^\s*"version":\s*"([^"]+)"""")))
        assertEquals(VersionRead.Failed("regex 'nothing(x)' matches nothing in package.json"), read(VersionSource.Regex("package.json", "nothing(x)")))
        assertEquals(VersionRead.Failed("regex '\"version\"' needs one capture group"), read(VersionSource.Regex("package.json", "\"version\"")))
    }

    /** Every component with a source, in this repository and SAFANORIA_EXTRA_REPOS, reads a version. */
    @Test
    fun realRepositories() {
        for (root in listOf(repoRoot) + extraRepos) {
            val repository = Repository(root)
            val sources = repository.config.components.values.mapNotNull { c -> c.version?.let { c.name to it } }
            assertTrue(sources.isNotEmpty() || root != repoRoot, "this repository has a version source")
            for ((name, source) in sources) {
                val found = Versions.read(repository, source)
                assertTrue(found is VersionRead.Found, "$root $name: $found")
            }
        }
    }
}
