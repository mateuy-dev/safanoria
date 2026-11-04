package dev.mateuy.safanoria.views

import dev.mateuy.safanoria.core.ReleaseRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShipFormTest {
    private val form = ShipForm(
        "big", "main", listOf("one"),
        components = listOf(ShipComponent("app", external = false, fromSource = "1.2.0"), ShipComponent("store", external = true, fromSource = null)),
    )

    @Test
    fun notStampingIsTheDefaultAndAlwaysShips() {
        assertNull(form.problem)
        assertEquals(emptyList(), form.releases)
        assertTrue("its own unfinished work too" in form.text && "Nothing is pushed" in form.text, form.text)
    }

    @Test
    fun stampingAsksForTheVersionsItCannotRead() {
        val stamping = form.toggleStamp()
        assertEquals("'store' is external: give the version it was released as", stamping.problem)
        assertEquals("'1.3' is not a version: MAJOR.MINOR.PATCH, e.g. 4.3.0", stamping.version("store", "1.3").problem)
        val filled = stamping.version("store", "1.3.0")
        assertNull(filled.problem)
        // The version as its source reads it is read there again; one typed is given.
        assertEquals(listOf(ReleaseRequest("app", null), ReleaseRequest("store", "1.3.0")), filled.releases)
        assertEquals(listOf(ReleaseRequest("app", "1.2.1")), filled.version("app", "1.2.1").toggle("store").releases)
        assertEquals("Choose a component to stamp, or don't stamp", filled.toggle("app").toggle("store").problem)
    }
}
