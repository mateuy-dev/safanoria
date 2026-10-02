package dev.mateuy.safanoria.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdsTest {
    @Test
    fun likeRealTitles() {
        // Titles like VacAppKMP's; where the real id differs, the suggestion is still sensible.
        assertEquals("andalucia-dual-file-import", Ids.suggest("Andalucia Dual-File Import"))
        assertEquals("ai-powered-excel-importer", Ids.suggest("AI-Powered Excel Importer using Koog"))
        assertEquals("birth-report-excel-importer", Ids.suggest("Birth Report Excel Importer"))
        assertEquals("leftovers-associations-branch-review", Ids.suggest("Leftovers from the associations branch review"))
        assertEquals("wire-apply-movement-sale", Ids.suggest("Wire ApplyMovementAsSale Route"))
    }

    @Test
    fun accentsAndOtherLanguages() {
        assertEquals("edicio-moviments-animals", Ids.suggest("Edició de moviments amb animals"))
        assertEquals("separar-lotes-cebo-nodrizas", Ids.suggest("Separar los lotes de cebo de las nodrizas"))
        assertEquals("colleccio-animals", Ids.suggest("Col·lecció d'animals"))
    }

    @Test
    fun camelCaseAndAcronyms() {
        assertEquals("http-server-timeout", Ids.suggest("HTTPServer timeout"))
        assertEquals("consolidated-animal-notebook", Ids.suggest("ConsolidatedAnimal notebook"))
    }

    @Test
    fun childrenAreNamedAfterTheirParent() {
        assertEquals("herd-locations-map-input", Ids.suggest("Map input for herd locations", parent = "herd-locations"))
        val long = Ids.suggest("Pick a location with a draggable pin", parent = "herd-locations-and-farm-buildings")!!
        assertTrue(long.length <= Ids.MAX_LENGTH && long.startsWith("herd-locations-and-farm-buildings-"), long)
    }

    @Test
    fun alwaysValidOrNull() {
        assertEquals("report", Ids.suggest("2024 report"))
        assertNull(Ids.suggest("!!!"))
        assertNull(Ids.suggest(""))
        assertEquals("the-of", Ids.suggest("The of")) // only filler: keep it rather than nothing
        for (title in listOf("x", "A very long title with many words that goes on and on", "Ünïcödé — çhars!")) {
            Ids.suggest(title)?.let { assertTrue(isValidId(it), "$title → $it") }
        }
    }
}
