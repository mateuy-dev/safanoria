package dev.mateuy.safanoria.gui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mateuy.safanoria.core.BoardGroup
import dev.mateuy.safanoria.core.TicketFilter
import dev.mateuy.safanoria.core.TicketType
import dev.mateuy.safanoria.views.BoardViewState
import dev.mateuy.safanoria.views.TicketStore
import dev.mateuy.safanoria.views.boardViewState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


class BoardViewModel(private val store: TicketStore) : ViewModel() {
    /**
     * Closed work is collapsed until asked for: it is most of the tickets and rarely what is looked
     * for. Not what is to release: that is what the next version brings.
     */
    private val collapsed = MutableStateFlow<Set<BoardGroup?>>(setOf(BoardGroup.RELEASED, BoardGroup.WONTFIX))

    private val filter = MutableStateFlow(TicketFilter())

    /** Children are hidden until asked for: their parent's card shows their progress, its screen their board. */
    private val showChildren = MutableStateFlow(false)

    val state: StateFlow<BoardViewState> = combine(store.snapshot, collapsed, filter, showChildren, ::boardViewState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoardViewState())

    /** Collapses the column of [group], or expands it when collapsed. */
    fun toggleColumn(group: BoardGroup?) {
        collapsed.update { if (group in it) it - group else it + group }
    }

    fun toggleType(type: TicketType) {
        filter.update { it.copy(types = it.types.toggled(type)) }
    }

    fun toggleArea(area: String) {
        filter.update { it.copy(areas = it.areas.toggled(area)) }
    }

    fun toggleTag(tag: String) {
        filter.update { it.copy(tags = it.tags.toggled(tag)) }
    }

    /** Only tickets with a `blockedBy` that isn't done. */
    fun toggleBlocked() {
        filter.update { it.copy(blocked = !it.blocked) }
    }

    fun toggleChildren() {
        showChildren.update { !it }
    }

    fun clearFilter() {
        filter.value = TicketFilter()
    }

    private fun <T> Set<T>.toggled(value: T) = if (value in this) this - value else this + value

    fun refresh() {
        viewModelScope.launch { store.refresh() }
    }

    /** Fetches the remotes, then reads the tickets again. */
    fun fetch() {
        viewModelScope.launch { store.fetch() }
    }
}
