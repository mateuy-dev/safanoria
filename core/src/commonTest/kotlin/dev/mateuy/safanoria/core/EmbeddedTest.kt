package dev.mateuy.safanoria.core

import io.github.optimumcode.json.schema.JsonSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmbeddedTest {
    @Test
    fun schemasAreEmbeddedAndLoad() {
        assertTrue(Embedded.TICKET_SCHEMA.contains("\"\$schema\""))
        JsonSchema.fromDefinition(Embedded.TICKET_SCHEMA)
        JsonSchema.fromDefinition(Embedded.CONFIG_SCHEMA)
    }

    /** Byte for byte, so a generator bug (e.g. markdown table rows losing their `|`) shows up. */
    @Test
    fun filesAreEmbeddedExactly() {
        fun file(path: String) = read(repoRoot / path).replace("\r\n", "\n") // Windows checkouts
        assertEquals(file("SPEC.md"), Embedded.SPEC)
        assertEquals(file("skill/SKILL.md"), Embedded.SKILL)
        assertEquals(file("templates/ticket.md"), Embedded.TICKET_TEMPLATE)
        assertEquals(file("templates/bug.md"), Embedded.TYPE_TEMPLATES["bug"])
        assertEquals(file("templates/research.md"), Embedded.TYPE_TEMPLATES["research"])
    }

    @Test
    fun versionIsSemver() {
        // Tests run on non-release builds, which add -dev.
        assertTrue(Regex("""\d+\.\d+\.\d+(-dev)?""").matches(Embedded.VERSION), Embedded.VERSION)
    }
}
