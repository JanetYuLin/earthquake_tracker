package io.github.janetyulin.grounded.ui.list

import io.github.janetyulin.grounded.domain.model.TimePeriod
import io.github.janetyulin.grounded.ui.common.FeedContent
import io.github.janetyulin.grounded.ui.common.LocationUiState

/**
 * Single UI State for the List screen.
 *
 * @param sortOptions The orders the user can pick; Nearest only appears when the location is known.
 */
data class ListUiState(
    val period: TimePeriod = TimePeriod.DAY,
    val content: FeedContent = FeedContent.Loading,
    val location: LocationUiState = LocationUiState.CHECKING,
    val sort: ListSortUiState = ListSortUiState.RECENT,
    val sortOptions: List<ListSortUiState> = listOf(ListSortUiState.RECENT, ListSortUiState.STRONGEST),
    val isRefreshing: Boolean = false
)

enum class ListSortUiState { RECENT, NEAREST, STRONGEST }

sealed interface ListUiEvent {
    data class SelectPeriod(val period: TimePeriod) : ListUiEvent
    data class SelectSort(val sort: ListSortUiState) : ListUiEvent
    data object Refresh : ListUiEvent

    /** The screen became visible or the permission dialog closed; re-read the location. */
    data object CheckLocation : ListUiEvent
}
