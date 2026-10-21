package dev.mateuy.safanoria.gui.versions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mateuy.safanoria.views.TicketStore
import dev.mateuy.safanoria.views.VersionsViewState
import dev.mateuy.safanoria.views.versionsViewState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


class VersionsViewModel(private val store: TicketStore) : ViewModel() {
    /** The component chosen; null until one is, which shows the project's first. */
    private val component = MutableStateFlow<String?>(null)

    val state: StateFlow<VersionsViewState> = combine(store.snapshot, component, ::versionsViewState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VersionsViewState())

    fun selectComponent(name: String) {
        component.value = name
    }

    fun refresh() {
        viewModelScope.launch { store.refresh() }
    }
}
