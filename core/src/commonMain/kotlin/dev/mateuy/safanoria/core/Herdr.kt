package dev.mateuy.safanoria.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Path

public class HerdrException(message: String) : Exception(message)

/** A Herdr workspace or tab that [Herdr.closing] found to belong to a ticket's worktree. */
public data class HerdrTarget(
    val kind: Kind,
    val id: String,
    /** True when the process asking is running in it: closing it ends that process, so it goes last. */
    val current: Boolean,
) {
    public enum class Kind(public val text: String) { WORKSPACE("workspace"), TAB("tab") }
}

/**
 * Safanoria inside Herdr (a terminal multiplexer for coding agents): opens a ticket's worktree as a
 * workspace or tab running Claude Code, and finds what to close when the ticket is merged. It talks to
 * the `herdr` CLI of the session this process runs in (`HERDR_ENV=1`); [currentPane] is the one this process runs in and [run]
 * how it runs a command line (tests give fakes).
 */
public class Herdr(
    private val currentPane: String? = environmentVariable("HERDR_PANE_ID"),
    private val run: (String) -> ProcessResult = ::runCommand,
) {
    public companion object {
        /** True when this process runs in a Herdr-managed pane. */
        public fun inside(): Boolean = environmentVariable("HERDR_ENV") == "1"

        /** Herdr agent names are `[a-z][a-z0-9_-]{0,31}`; a ticket id (SPEC §3) may start with a digit or have a dot. */
        public fun agentName(id: String): String {
            val name = id.lowercase().map { if (it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_') it else '-' }.joinToString("")
            return (if (name.firstOrNull() in 'a'..'z') name else "t-$name").take(32)
        }
    }

    private val json = Json

    private fun herdr(vararg args: String): JsonObject {
        val result = run((listOf("herdr") + args).joinToString(" ") { quote(it) } + " 2>&1")
        if (result.exitCode != 0) throw HerdrException("herdr ${args.take(2).joinToString(" ")} failed (${result.exitCode}): ${result.output.trim()}")
        return try {
            json.parseToJsonElement(result.output).jsonObject["result"]!!.jsonObject
        } catch (e: Exception) {
            throw HerdrException("herdr ${args.take(2).joinToString(" ")}: unexpected output: ${result.output.trim().take(200)}")
        }
    }

    private fun JsonElement.string(key: String): String? = jsonObject[key]?.jsonPrimitive?.contentOrNull

    /**
     * Opens [dir] in a new Herdr workspace, or a tab of the current one, labelled [id], and starts Claude Code there
     * named [id] with [prompt]. Focus stays where it is. Returns what it opened. Nothing is closed when Claude
     * can't start: the shell is left for the user.
     */
    public fun open(dir: Path, id: String, prompt: String, workspace: Boolean): String {
        val created = if (workspace) {
            herdr("workspace", "create", "--cwd", dir.toString(), "--label", id, "--no-focus")
        } else {
            val current = environmentVariable("HERDR_WORKSPACE_ID") ?: throw HerdrException("HERDR_WORKSPACE_ID isn't set: not in a Herdr pane?")
            herdr("tab", "create", "--workspace", current, "--cwd", dir.toString(), "--label", id, "--no-focus")
        }
        val pane = created["root_pane"]?.string("pane_id") ?: throw HerdrException("herdr didn't return the new pane")
        val kind = if (workspace) "workspace" else "tab"
        try {
            herdr("agent", "start", agentName(id), "--kind", "claude", "--pane", pane, "--", "--name", id, prompt)
        } catch (e: HerdrException) {
            throw HerdrException("opened a $kind for $id (pane $pane) but Claude didn't start there: ${e.message}")
        }
        return "opened a $kind '$id' running Claude Code"
    }

    /**
     * What to close for a ticket whose worktree is [dir]: the workspaces and tabs with a pane in it. A workspace
     * whose every tab is in it closes whole; otherwise only those tabs. The one this process runs in comes last.
     */
    public fun closing(dir: Path): List<HerdrTarget> {
        val inside = herdr("pane", "list")["panes"]!!.jsonArray.filter { pane ->
            listOf("cwd", "foreground_cwd").any { key -> pane.string(key)?.let { it == dir.toString() || it.startsWith("$dir/") } == true }
        }
        if (inside.isEmpty()) return emptyList()
        val tabCount = herdr("workspace", "list")["workspaces"]!!.jsonArray
            .associate { it.string("workspace_id") to it.jsonObject["tab_count"]!!.jsonPrimitive.int }
        val here = currentPane
        val targets = mutableListOf<HerdrTarget>()
        for ((workspace, panes) in inside.groupBy { it.string("workspace_id") }) {
            val tabs = panes.mapNotNull { it.string("tab_id") }.distinct()
            val current = panes.any { it.string("pane_id") == here }
            if (tabs.size == tabCount[workspace]) {
                targets += HerdrTarget(HerdrTarget.Kind.WORKSPACE, workspace!!, current)
            } else {
                tabs.forEach { tab -> targets += HerdrTarget(HerdrTarget.Kind.TAB, tab, panes.any { it.string("tab_id") == tab && it.string("pane_id") == here }) }
            }
        }
        return targets.sortedBy { it.current }
    }

    /** Closes [target]. If this process runs in it, it ends here. */
    public fun close(target: HerdrTarget) {
        herdr(target.kind.text, "close", target.id)
    }
}
