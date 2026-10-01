package dev.mateuy.safanoria.core

import okio.Path

public enum class Severity { ERROR, WARNING }

/**
 * A problem found in a file. Every command reports problems as diagnostics; `line` and `column`
 * are 1-based and refer to the file, not to the frontmatter block.
 */
public data class Diagnostic(
    val file: Path?,
    val line: Int?,
    val column: Int?,
    val code: String,
    val message: String,
    val severity: Severity = Severity.ERROR,
) {
    /** `file:line:column: error[code]: message`, the format compilers use, so editors can jump to it. */
    override fun toString(): String = buildString {
        if (file != null) {
            append(file)
            if (line != null) append(':').append(line)
            if (line != null && column != null) append(':').append(column)
            append(": ")
        }
        append(severity.name.lowercase()).append('[').append(code).append("]: ").append(message)
    }
}
