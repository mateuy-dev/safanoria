package dev.mateuy.safanoria.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.mateuy.safanoria.gui.board.BoardScreen
import dev.mateuy.safanoria.gui.board.BoardViewModel
import dev.mateuy.safanoria.gui.navigation.BoardRoute
import dev.mateuy.safanoria.gui.navigation.Route
import dev.mateuy.safanoria.gui.navigation.TicketRoute
import dev.mateuy.safanoria.gui.navigation.VersionsRoute
import dev.mateuy.safanoria.gui.theme.SafanoriaTheme
import dev.mateuy.safanoria.gui.ticket.TicketScreen
import dev.mateuy.safanoria.gui.ticket.TicketViewModel
import dev.mateuy.safanoria.gui.versions.VersionsScreen
import dev.mateuy.safanoria.gui.versions.VersionsViewModel

/** The app: the theme and the navigation between screens. Each back stack entry owns its ViewModel. */
@Composable
fun App(container: AppContainer) {
    // Read at the start, and again whenever the window gets the focus back: the tickets change
    // outside the app (a terminal, an agent session), mostly while the user is away from it.
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(container, focused) {
        if (focused || container.ticketStore.snapshot.value.graph == null) container.ticketStore.refresh()
    }
    SafanoriaTheme {
        val backStack = remember { mutableStateListOf<Route>(BoardRoute) }
        NavDisplay(
            backStack = backStack,
            onBack = { if (backStack.size > 1) backStack.removeLast() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<BoardRoute> {
                    BoardScreen(
                        viewModel = viewModel { BoardViewModel(container.ticketStore) },
                        onOpenTicket = { backStack.add(TicketRoute(it)) },
                        onOpenVersions = { backStack.add(VersionsRoute) },
                    )
                }
                entry<VersionsRoute> {
                    VersionsScreen(
                        viewModel = viewModel { VersionsViewModel(container.ticketStore) },
                        onOpenTicket = { backStack.add(TicketRoute(it)) },
                        onBack = { if (backStack.size > 1) backStack.removeLast() },
                    )
                }
                entry<TicketRoute> { route ->
                    TicketScreen(
                        viewModel = viewModel { TicketViewModel(route.id, container.ticketStore, container.terminal) },
                        onOpenTicket = { backStack.add(TicketRoute(it)) },
                        onBack = { if (backStack.size > 1) backStack.removeLast() },
                    )
                }
            },
        )
    }
}
