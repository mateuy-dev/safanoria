package dev.mateuy.safanoria.core

/** A `MAJOR.MINOR.PATCH` version (SPEC §9), as in schema/ticket.schema.json. */
public data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
    override fun compareTo(other: Version): Int = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
    override fun toString(): String = "$major.$minor.$patch"

    public companion object {
        private val PATTERN = Regex("""^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$""")

        /** Null unless [text] is exactly `MAJOR.MINOR.PATCH` (no `v`, no suffix). */
        public fun parse(text: String): Version? = PATTERN.matchEntire(text)?.destructured?.let { (a, b, c) ->
            runCatching { Version(a.toInt(), b.toInt(), c.toInt()) }.getOrNull()
        }
    }
}

public sealed interface VersionRead {
    public data class Found(val version: Version) : VersionRead
    public data class Failed(val reason: String) : VersionRead
}

/** Reads a component's current version from its `version` source (SPEC §2). */
public object Versions {
    public fun read(repository: Repository, source: VersionSource): VersionRead {
        val path = repository.root / source.file
        val fs = repository.fileSystem
        if (!fs.exists(path)) return VersionRead.Failed("${source.file} not found")
        val text = fs.read(path) { readUtf8() }
        val raw = when (source) {
            is VersionSource.Property -> property(text, source.property)
                ?: return VersionRead.Failed("no '${source.property}=' line in ${source.file}")
            is VersionSource.Regex -> {
                val regex = runCatching { Regex(source.regex, RegexOption.MULTILINE) }.getOrElse {
                    return VersionRead.Failed("invalid regex '${source.regex}': ${it.message}")
                }
                val match = regex.find(text) ?: return VersionRead.Failed("regex '${source.regex}' matches nothing in ${source.file}")
                // groups[1] throws on the JVM when there is no group, so check the count first.
                match.groups.takeIf { it.size > 1 }?.get(1)?.value
                    ?: return VersionRead.Failed("regex '${source.regex}' needs one capture group")
            }
        }
        return Version.parse(raw)?.let { VersionRead.Found(it) }
            ?: VersionRead.Failed("'$raw' in ${source.file} is not MAJOR.MINOR.PATCH")
    }

    /** The value of `key=value` or `key = value`; `#` and `!` lines are comments. Last one wins, as in Java properties. */
    private fun property(text: String, key: String): String? = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("!") }
        .mapNotNull { line ->
            val sep = line.indexOfFirst { it == '=' || it == ':' }
            if (sep > 0 && line.substring(0, sep).trim() == key) line.substring(sep + 1).trim() else null
        }
        .lastOrNull()
}
