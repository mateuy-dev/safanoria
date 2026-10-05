package dev.mateuy.safanoria.cli

import com.github.ajalt.clikt.testing.test
import dev.mateuy.safanoria.core.environmentVariable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListTest {
    private val repoRoot = (environmentVariable("SAFANORIA_REPO_ROOT") ?: error("run through Gradle")).toPath()
    private val fixture = repoRoot / "core" / "src" / "commonTest" / "fixtures" / "validate" / "valid"

    private fun run(vararg args: String) = cli().test(listOf("--root", fixture.toString(), "list", "--checkout") + args.toList())
    private fun ids(stdout: String) = stdout.lines().filter { it.isNotBlank() }.map { it.substringBefore(' ') }

    @Test
    fun allInBoardOrder() {
        val r = run()
        assertEquals(0, r.statusCode, r.output)
        assertEquals(
            listOf("herd-locations", "herd-locations-map", "user-request", "herd-locations-model", "map-tiles-study", "closed"),
            ids(r.stdout),
        )
        val lines = r.stdout.lines()
        assertTrue(lines[0].startsWith("herd-locations        in-progress  medium  feature   S  Ticket herd-locations  [1/3]"), lines[0])
        assertTrue(lines[1].endsWith("Ticket herd-locations-map  parent herd-locations"), lines[1])
        assertTrue(lines[5].endsWith("Ticket closed  #registry"), lines[5])
    }

    @Test
    fun filters() {
        assertEquals(listOf("herd-locations-model", "map-tiles-study"), ids(run("--status", "done").stdout))
        assertEquals(listOf("herd-locations", "herd-locations-model", "map-tiles-study"), ids(run("--status", "in-progress,done").stdout))
        assertEquals(listOf("map-tiles-study"), ids(run("--type", "research").stdout))
        assertEquals(listOf("herd-locations-map", "herd-locations-model"), ids(run("--parent", "herd-locations").stdout))
        assertEquals(emptyList(), ids(run("--blocked").stdout), "its blocker is done")
        assertEquals(emptyList(), ids(run("--area", "app").stdout), "no ticket sets area")
        assertEquals(listOf("closed"), ids(run("--tag", "registry,offline").stdout))
        assertEquals(emptyList(), ids(run("--tag", "offline").stdout))
        // test() bypasses main, which maps usage errors to exit 2: checked on the binary.
        assertTrue(run("--status", "open").stderr.contains("invalid choice: open"))
    }

    @Test
    fun json() {
        val r = run("--format", "json", "--parent", "herd-locations")
        assertEquals(0, r.statusCode, r.output)
        val tickets = Json.parseToJsonElement(r.stdout).jsonObject.getValue("tickets").jsonArray.map { it.jsonObject }
        val map = tickets.first { it.str("id") == "herd-locations-map" }
        assertEquals("tickets/herd-locations-map.md", map.str("file"))
        assertEquals(emptyList(), map.getValue("tags").jsonArray.toList())
        assertEquals(listOf("herd-locations-model"), map.getValue("blockedBy").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(emptyList(), map.getValue("openBlockers").jsonArray.toList())
        val model = tickets.first { it.str("id") == "herd-locations-model" }
        assertEquals(listOf("herd-locations-map"), model.getValue("blocks").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("1.0.0", model.getValue("resolvedIn").jsonObject.str("app"))
    }

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
}
