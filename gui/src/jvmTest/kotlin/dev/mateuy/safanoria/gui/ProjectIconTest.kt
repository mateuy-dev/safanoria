package dev.mateuy.safanoria.gui

import androidx.compose.ui.unit.Density
import dev.mateuy.safanoria.gui.theme.IconFile
import dev.mateuy.safanoria.gui.theme.readProjectIcon
import dev.mateuy.safanoria.gui.theme.toPainter
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProjectIconTest {
    private val root: File = Files.createTempDirectory("safanoria-icon").toFile().also { it.deleteOnExit() }
    private val density = Density(1f)

    private fun write(path: String, bytes: ByteArray) = root.resolve(path).apply { parentFile.mkdirs() }.writeBytes(bytes)

    private fun png(): ByteArray = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB), "png", it) }.toByteArray()

    @Test
    fun svgAndPngAreRead() {
        write("design/app.SVG", """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 8 8"><rect width="8" height="8"/></svg>""".toByteArray())
        write("app.png", png())
        assertNotNull(assertNotNull(readProjectIcon(root, "design/app.SVG")).toPainter(density))
        val painter = assertNotNull(assertNotNull(readProjectIcon(root, "app.png")).toPainter(density))
        assertEquals(4f, painter.intrinsicSize.width)
    }

    /** Each of these leaves the app with Safanoria's icon. */
    @Test
    fun noIconWithoutTheKeyTheFileOrAnImage() {
        assertNull(readProjectIcon(root, null))
        assertNull(readProjectIcon(root, "missing.png"))
        write("notes.png", "not an image".toByteArray())
        assertNull(assertNotNull(readProjectIcon(root, "notes.png")).toPainter(density))
        assertNull(IconFile("not an image".toByteArray(), svg = true).toPainter(density))
    }
}
