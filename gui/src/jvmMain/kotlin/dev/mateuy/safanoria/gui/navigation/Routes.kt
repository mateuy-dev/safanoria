package dev.mateuy.safanoria.gui.navigation

/** A screen and its arguments: the keys of the Navigation 3 back stack. */
sealed interface Route

data object BoardRoute : Route

data class TicketRoute(val id: String) : Route
