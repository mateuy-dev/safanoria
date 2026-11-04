package dev.mateuy.safanoria.views

import kotlin.test.Test
import kotlin.test.assertTrue

class ShipFormTest {
    @Test
    fun theQuestionSaysWhatShipsAndThatTheReleaseStampsIt() {
        val text = ShipForm("big", "main", listOf("one")).text
        assertTrue("bringing one" in text && "its own unfinished work too" in text, text)
        assertTrue("The next release stamps them" in text && "Nothing is pushed" in text, text)
    }
}
