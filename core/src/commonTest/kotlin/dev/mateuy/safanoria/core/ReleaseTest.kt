package dev.mateuy.safanoria.core

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReleaseTest {
    private val root = "/repo".toPath()
    private val today = "2026-10-02"

    /** A valid ticket; [front] goes after `updated`. */
    private fun ticket(id: String, status: String = "done", type: String = "feature", front: String) =
        "---\nid: $id\ntype: $type\ntitle: Ticket $id\nstatus: $status\npriority: medium\nsize: S\n" +
            "created: 2026-10-01\nupdated: 2026-10-01\n$front---\n\n## Objective\n\nWhy.\n\n" +
            "## Acceptance Criteria\n\n- [x] It works\n\n## Plan\n\n- [x] Do it\n\n" +
            "## Work Log\n\n- **2026-10-01** · status · Started.\n"

    /** Like VacAppKMP: two components with sources and one external. */
    private val fs = FakeFileSystem().apply {
        createDirectories(root / "tickets")
        createDirectories(root / "server")
        write(root / "safanoria.yaml") {
            writeUtf8(
                "safanoria: 1\nworktree: ../project--{id}\ncomponents:\n" +
                    "  app:\n    version: { file: gradle.properties, property: appVersionName }\n" +
                    "  ktor:\n    version: { file: server/build.gradle.kts, regex: 'version = \"([^\"]+)\"' }\n" +
                    "  rails:\n    external: true\n",
            )
        }
        write(root / "gradle.properties") { writeUtf8("appVersionName=4.3.0\n") }
        write(root / "server" / "build.gradle.kts") { writeUtf8("version = \"1.9.0\"\n") }
        val tickets = mapOf(
            "app-fix" to ticket("app-fix", front = "area: [app]\n"),
            "both-parts" to ticket("both-parts", front = "area: [app, ktor]\n"),
            "app-shipped" to ticket("app-shipped", front = "area: [app]\nresolvedIn:\n  app: 4.2.0\n"),
            "app-review" to ticket("app-review", status = "review", front = "area: [app]\n"),
            "app-study" to ticket("app-study", type = "research", front = "area: [app]\n"),
            "server-fix" to ticket("server-fix", front = "area: [ktor]\nresolvedIn:\n  ktor: null\n"),
            "rails-one" to ticket("rails-one", front = "area: [rails]\n"),
            "rails-two" to ticket("rails-two", front = "area: [rails]\n"),
        )
        tickets.forEach { (id, text) -> write(root / "tickets" / "$id.md") { writeUtf8(text) } }
    }

    private fun repo() = Repository(root, fs)
    private fun prepare(component: String, version: String? = null, only: List<String>? = null) =
        Release.prepare(repo(), ReleaseRequest(component, version, only), today)
    private fun ready(component: String, version: String? = null, only: List<String>? = null) =
        assertIs<ReleaseResult.Ready>(prepare(component, version, only))
    private fun refused(component: String, version: String? = null, only: List<String>? = null) =
        assertIs<ReleaseResult.Refused>(prepare(component, version, only)).reason
    private fun write(r: ReleaseResult.Ready) = r.files.forEach { f -> fs.write(f.path) { writeUtf8(f.text) } }

    @Test
    fun stampsDoneTicketsOfTheComponent() {
        val r = ready("app")
        assertEquals(Version(4, 3, 0), r.version)
        assertTrue(r.versionFromSource)
        assertEquals(listOf("app-fix", "both-parts"), r.stamped)
        assertEquals(emptyList(), r.warnings)

        val before = fs.read(root / "tickets" / "app-fix.md") { readUtf8() }
        val after = r.files.first { it.path.name == "app-fix.md" }.text
        val expected = before
            .replace("updated: 2026-10-01\narea: [app]\n", "updated: 2026-10-02\narea: [app]\nresolvedIn:\n  app: 4.3.0\n")
            .replace("· Started.\n", "· Started.\n- **2026-10-02** · release · app 4.3.0\n")
        assertEquals(expected, after, "only resolvedIn, updated and the Work Log change")

        write(r)
        assertEquals(emptyList(), Validator(repo()).validate().map { it.toString() })
        assertEquals(emptyList(), ready("app").stamped, "nothing left to stamp")
        assertEquals(emptyList(), ready("app", "4.3.0").stamped, "same version again is allowed")
    }

    @Test
    fun componentsAreIndependent() {
        write(ready("app"))
        val ktor = ready("ktor")
        assertEquals(Version(1, 9, 0), ktor.version, "read with the regex source")
        assertEquals(listOf("both-parts", "server-fix"), ktor.stamped, "server-fix had ktor: null")
        write(ktor)
        val both = fs.read(root / "tickets" / "both-parts.md") { readUtf8() }
        assertTrue(both.contains("resolvedIn:\n  app: 4.3.0\n  ktor: 1.9.0\n"), both)
        assertEquals(2, Regex("· release ·").findAll(both).count())
        assertEquals(emptyList(), Validator(repo()).validate().map { it.toString() })
    }

    @Test
    fun givenVersion() {
        val r = ready("app", "4.4.0")
        assertEquals(Version(4, 4, 0), r.version)
        assertEquals(listOf("gradle.properties says 4.3.0, stamping 4.4.0 as given"), r.warnings)
        assertTrue(refused("app", "4.1.0").contains("lower than 4.2.0"))
        assertTrue(refused("app", "4.3").contains("not a version"))
        assertTrue(refused("web").contains("not a component"))
    }

    @Test
    fun externalComponent() {
        assertTrue(refused("rails").contains("external: give the version"))
        assertEquals(listOf("rails-one", "rails-two"), ready("rails", "2.8.0").stamped)
        // Done here doesn't say whether it was in that release: name the tickets.
        assertEquals(listOf("rails-two"), ready("rails", "2.8.0", only = listOf("rails-two")).stamped)
        assertTrue(refused("rails", "2.8.0", only = listOf("app-fix")).contains("'rails' is not in its area (app)"))
        assertTrue(refused("app", only = listOf("app-review")).contains("it is review, not done"))
        assertTrue(refused("app", only = listOf("app-shipped")).contains("already has app 4.2.0"))
        assertTrue(refused("app", only = listOf("nope")).contains("no ticket 'nope'"))
    }

    @Test
    fun unreadableSource() {
        fs.write(root / "gradle.properties") { writeUtf8("appVersionName=4.3.0-SNAPSHOT\n") }
        assertEquals("can't read the version of 'app': '4.3.0-SNAPSHOT' in gradle.properties is not MAJOR.MINOR.PATCH", refused("app"))
        assertEquals(listOf("app-fix", "both-parts"), ready("app", "4.3.0").stamped, "a given version doesn't need the source")
    }
}
