package dev.mateuy.safanoria.gui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import org.jetbrains.compose.resources.decodeToSvgPainter

/**
 * The app's icon, from `design/` at the repository root: the variant drawn for small sizes
 * (window, taskbar, title bars). `safanoria-icon.svg` is the detailed one, for large sizes.
 */
@Composable
fun rememberAppIcon(): Painter {
    val density = LocalDensity.current
    return remember(density) {
        val bytes = checkNotNull(object {}.javaClass.getResourceAsStream("/safanoria-icon-small.svg")) { "icon missing from the app" }
            .use { it.readBytes() }
        bytes.decodeToSvgPainter(density)
    }
}
