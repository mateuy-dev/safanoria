package dev.mateuy.safanoria.gui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.decodeToSvgPainter
import java.io.File

/** An image file's content. [svg] says how to decode it. */
class IconFile(val bytes: ByteArray, val svg: Boolean)

/**
 * The file `icon` of `safanoria.yaml` names (SPEC §2), relative to [root]. Null when the project
 * has none or it can't be read.
 */
fun readProjectIcon(root: File, icon: String?): IconFile? {
    if (icon == null) return null
    val file = root.resolve(icon)
    val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
    return IconFile(bytes, svg = file.extension.equals("svg", ignoreCase = true))
}

/** Null when the content isn't an image of the kind its name says. */
fun IconFile.toPainter(density: Density): Painter? =
    runCatching { if (svg) bytes.decodeToSvgPainter(density) else BitmapPainter(bytes.decodeToImageBitmap()) }.getOrNull()

/** The icon of the project whose tickets are open; null (the default) when it has none. */
val LocalProjectIcon = compositionLocalOf<IconFile?> { null }

/**
 * The icon the app shows (window, taskbar, title bars): the project's, so that windows of
 * different projects can be told apart, else Safanoria's, from `design/` at the repository root:
 * the variant drawn for small sizes. `safanoria-icon.svg` is the detailed one, for large sizes.
 */
@Composable
fun rememberAppIcon(): Painter {
    val density = LocalDensity.current
    val project = LocalProjectIcon.current
    return remember(density, project) {
        project?.toPainter(density) ?: run {
            val bytes = checkNotNull(object {}.javaClass.getResourceAsStream("/safanoria-icon-small.svg")) { "icon missing from the app" }
                .use { it.readBytes() }
            bytes.decodeToSvgPainter(density)
        }
    }
}
