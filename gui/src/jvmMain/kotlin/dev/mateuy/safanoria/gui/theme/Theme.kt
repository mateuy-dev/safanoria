package dev.mateuy.safanoria.gui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.mateuy.safanoria.core.Priority
import dev.mateuy.safanoria.core.Status

// Safanoria is Catalan for carrot.
private val Carrot = Color(0xFFE8731A)

private val Light = lightColorScheme(primary = Carrot, secondary = Color(0xFF4F7A3A))
private val Dark = darkColorScheme(primary = Color(0xFFFFA05C), secondary = Color(0xFF9CCB84))

@Composable
fun SafanoriaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

val Status.color: Color
    get() = when (this) {
        Status.IN_PROGRESS -> Color(0xFF2F7DD1)
        Status.REVIEW -> Color(0xFF8E5BD0)
        Status.READY -> Color(0xFF2E9E6B)
        Status.BACKLOG -> Color(0xFF8A8F98)
        Status.DONE -> Color(0xFF5C6B5E)
        Status.WONTFIX -> Color(0xFFA0766B)
    }

val Status.label: String
    get() = when (this) {
        Status.IN_PROGRESS -> "In progress"
        Status.REVIEW -> "Review"
        Status.READY -> "Ready"
        Status.BACKLOG -> "Backlog"
        Status.DONE -> "Done"
        Status.WONTFIX -> "Won't fix"
    }

val Priority.color: Color
    get() = when (this) {
        Priority.URGENT -> Color(0xFFD13B3B)
        Priority.HIGH -> Color(0xFFE8731A)
        Priority.MEDIUM -> Color(0xFF8A8F98)
        Priority.LOW, Priority.VERY_LOW -> Color(0xFFB4B8BF)
    }

/** `validate` warnings; errors use the theme's error color. */
val WarningColor = Color(0xFFB7791F)
