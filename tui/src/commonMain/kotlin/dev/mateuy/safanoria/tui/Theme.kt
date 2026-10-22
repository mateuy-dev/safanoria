package dev.mateuy.safanoria.tui

import com.github.ajalt.colormath.Color
import com.github.ajalt.colormath.model.RGB
import com.github.ajalt.mordant.rendering.TextStyle
import dev.mateuy.safanoria.core.BoardGroup
import dev.mateuy.safanoria.core.Priority
import dev.mateuy.safanoria.core.Status

/**
 * The desktop app's colors (gui/theme), as text colors: no backgrounds but for what is selected,
 * so that light and dark terminals both read well.
 */
internal object Theme {
    // Safanoria is Catalan for carrot.
    val carrot = RGB("#E8731A")
    val accent = TextStyle(carrot, bold = true)
    val bold = TextStyle(bold = true)
    val dim = TextStyle(dim = true)
    val error = TextStyle(RGB("#D13B3B"))
    val warning = TextStyle(RGB("#B7791F"))
    val success = TextStyle(RGB("#2E9E6B"))
    val selected = TextStyle(inverse = true)
    val link = TextStyle(underline = true)

    fun status(status: Status): Color = when (status) {
        Status.IN_PROGRESS -> RGB("#2F7DD1")
        Status.REVIEW -> RGB("#8E5BD0")
        Status.READY -> RGB("#2E9E6B")
        Status.BACKLOG -> RGB("#8A8F98")
        Status.DONE -> RGB("#5C6B5E")
        Status.WONTFIX -> RGB("#A0766B")
    }

    /** A board column's: its status's, but for what is to release, which is the one to look at. */
    fun group(group: BoardGroup?): Color = when (group) {
        null -> RGB("#D13B3B")
        BoardGroup.TO_RELEASE -> RGB("#1F9AA8")
        else -> status(group.status)
    }

    fun priority(priority: Priority): Color = when (priority) {
        Priority.URGENT -> RGB("#D13B3B")
        Priority.HIGH -> carrot
        Priority.MEDIUM -> RGB("#8A8F98")
        Priority.LOW, Priority.VERY_LOW -> RGB("#B4B8BF")
    }
}

internal val Status.label: String
    get() = when (this) {
        Status.IN_PROGRESS -> "In progress"
        Status.REVIEW -> "Review"
        Status.READY -> "Ready"
        Status.BACKLOG -> "Backlog"
        Status.DONE -> "Done"
        Status.WONTFIX -> "Won't fix"
    }

internal val BoardGroup?.columnLabel: String get() = this?.heading ?: "Unreadable status"
