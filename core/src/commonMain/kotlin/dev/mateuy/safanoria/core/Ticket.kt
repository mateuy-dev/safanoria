package dev.mateuy.safanoria.core

import okio.Path

/**
 * A ticket file. Parsing is lazy: [frontmatter] parses only the YAML block, [body] the rest, so
 * commands that only need ids and relations stay fast on large repositories.
 */
public class Ticket(public val path: Path, public val text: String) {
    /** The id from the filename (SPEC §3: the filename is `<id>.md`). */
    public val fileId: String get() = path.name.removeSuffix(".md")

    private val lines by lazy { text.split('\n') }
    private val parsedFrontmatter by lazy { Frontmatter.parse(path, text) }
    private val bounds by lazy { Frontmatter.bounds(lines) }

    public val frontmatter: Frontmatter? get() = parsedFrontmatter.first

    /** The body after the frontmatter; the whole file when there is no frontmatter. */
    public val body: Body by lazy {
        val close = bounds?.closeLine ?: 0
        Body(path, lines.drop(close), firstLine = close + 1)
    }

    /** Problems found while parsing: frontmatter syntax, then malformed body parts. */
    public val parseDiagnostics: List<Diagnostic> get() = parsedFrontmatter.second + body.diagnostics
}
