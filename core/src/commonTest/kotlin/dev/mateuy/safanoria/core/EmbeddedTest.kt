package dev.mateuy.safanoria.core

import io.github.optimumcode.json.schema.JsonSchema
import kotlin.test.Test
import kotlin.test.assertTrue

class EmbeddedTest {
    @Test
    fun schemasAreEmbeddedAndLoad() {
        assertTrue(Embedded.TICKET_SCHEMA.contains("\"\$schema\""))
        JsonSchema.fromDefinition(Embedded.TICKET_SCHEMA)
        JsonSchema.fromDefinition(Embedded.CONFIG_SCHEMA)
    }

    @Test
    fun versionIsSet() {
        assertTrue(Embedded.VERSION.isNotBlank())
    }
}
