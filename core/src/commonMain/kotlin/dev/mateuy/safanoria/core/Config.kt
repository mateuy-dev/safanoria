package dev.mateuy.safanoria.core

import okio.FileSystem
import okio.Path

public const val CONFIG_FILE: String = "safanoria.yaml"

/** Where a component's current version is read from (SPEC §2). */
public sealed interface VersionSource {
    public val file: String

    /** A `key=value` file, e.g. `gradle.properties`. */
    public data class Property(override val file: String, val property: String) : VersionSource

    /** Any file; the regex has one capture group. */
    public data class Regex(override val file: String, val regex: String) : VersionSource
}

public data class Component(
    val name: String,
    /** Null when the component is `external` or its source is malformed (schema reports it). */
    val version: VersionSource?,
    val external: Boolean,
    val line: Int,
)

public data class RefSystem(val name: String, val url: String)

/**
 * `safanoria.yaml` with SPEC §2 defaults applied. Values of the wrong type fall back to their
 * default here; the schema check (step 3 of cli-core) reports them.
 */
public data class Config(
    val path: Path,
    val specVersion: Int?,
    val dir: String = "tickets",
    val mainBranch: String = "main",
    val worktree: String? = null,
    val components: Map<String, Component> = emptyMap(),
    val channels: List<String> = DEFAULT_CHANNELS,
    val userRef: String? = null,
    val refs: Map<String, RefSystem> = emptyMap(),
    val learningTargets: List<String> = emptyList(),
    /** File line of each top-level key, for diagnostics. */
    val keyLines: Map<String, Int> = emptyMap(),
) {
    public companion object {
        public val DEFAULT_CHANNELS: List<String> = listOf("email", "phone", "in-person", "other")
    }
}

public class ConfigResult(public val config: Config?, public val diagnostics: List<Diagnostic>)

public object ConfigLoader {
    /** Finds the repository root: [start] or the nearest parent containing `safanoria.yaml`. */
    public fun findRoot(fileSystem: FileSystem, start: Path): Path? {
        var dir: Path? = start
        while (dir != null) {
            if (fileSystem.exists(dir / CONFIG_FILE)) return dir
            dir = dir.parent
        }
        return null
    }

    public fun load(fileSystem: FileSystem, root: Path): ConfigResult {
        val path = root / CONFIG_FILE
        if (!fileSystem.exists(path)) {
            return ConfigResult(null, listOf(Diagnostic(path, null, null, "config-missing", "$CONFIG_FILE not found")))
        }
        return parse(path, fileSystem.read(path) { readUtf8() })
    }

    public fun parse(path: Path, text: String): ConfigResult {
        val (block, error) = parseYamlBlock(path, text, firstLine = 1)
        if (block == null) return ConfigResult(null, listOfNotNull(error))
        val root = block.root
        val components = root.get("components").mapEntries().associate { (key, value) ->
            val source = value.get("version")?.let { v ->
                val file = v.get("file").text()
                val property = v.get("property").text()
                val regex = v.get("regex").text()
                when {
                    file != null && property != null && regex == null -> VersionSource.Property(file, property)
                    file != null && regex != null && property == null -> VersionSource.Regex(file, regex)
                    else -> null
                }
            }
            key.content to Component(key.content, source, value.get("external").text() == "true", block.lineOf(key))
        }
        val refs = root.get("refs").mapEntries().mapNotNull { (key, value) ->
            value.get("url").text()?.let { key.content to RefSystem(key.content, it) }
        }.toMap()
        val config = Config(
            path = path,
            specVersion = root.get("safanoria").text()?.toIntOrNull(),
            dir = root.get("dir").text() ?: "tickets",
            mainBranch = root.get("mainBranch").text() ?: "main",
            worktree = root.get("worktree").text(),
            components = components,
            channels = root.get("channels").textList() ?: Config.DEFAULT_CHANNELS,
            userRef = root.get("userRef").text(),
            refs = refs,
            learningTargets = root.get("learningTargets").textList() ?: emptyList(),
            keyLines = root.mapEntries().associate { (key, _) -> key.content to block.lineOf(key) },
        )
        return ConfigResult(config, emptyList())
    }
}
