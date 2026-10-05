package dev.mateuy.safanoria.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
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
import dev.mateuy.safanoria.gui.theme.SafanoriaTheme
import dev.mateuy.safanoria.gui.ticket.TicketScreen
import dev.mateuy.safanoria.gui.ticket.TicketViewModel

/** The app: the theme and the navigation between screens. Each back stack entry owns its ViewModel. */
@Composable
fun App(container: AppContainer) {
    // The first read; screens ask for later ones.
    LaunchedEffect(container) { container.ticketStore.refresh() }
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
