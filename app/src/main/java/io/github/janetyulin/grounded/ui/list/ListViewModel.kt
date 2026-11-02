package io.github.janetyulin.grounded.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.janetyulin.grounded.core.coroutines.WhileUiSubscribed
import io.github.janetyulin.grounded.data.model.pointOrNull
import io.github.janetyulin.grounded.data.repository.EarthquakeRepository
import io.github.janetyulin.grounded.data.repository.LocationRepository
import io.github.janetyulin.grounded.data.repository.UserPreferencesRepository
import io.github.janetyulin.grounded.ui.common.EarthquakeUiState
import io.github.janetyulin.grounded.ui.common.toFeedContent
import io.github.janetyulin.grounded.ui.common.toUiState
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the List screen: the same earthquakes as the Map, sortable, with distances from the
 * device when its location is known.
 *
 * Fetching is owned by the foreground poller (which also reacts to a period change); this ViewModel
 * observes the cached feed and only refreshes on pull-to-refresh or Retry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ListViewModel @Inject constructor(
    private val earthquakeRepository: EarthquakeRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val locationRepository: LocationRepository,
    private val clock: Clock
) : ViewModel() {

    private val requestedSort = MutableStateFlow(ListSortUiState.RECENT)
    private val isRefreshing = MutableStateFlow(false)

    private val period = preferencesRepository.preferences.map { it.selectedPeriod }.distinctUntilChanged()
    private val feed = period.flatMapLatest { current -> earthquakeRepository.feed(current).map { current to it } }

    val uiState: StateFlow<ListUiState> = combine(
        feed,
        locationRepository.location,
        requestedSort,
        isRefreshing
    ) { (currentPeriod, feedState), location, requested, refreshing ->
        val here = location.pointOrNull
        val sortOptions = listOfNotNull(
            ListSortUiState.RECENT,
            ListSortUiState.NEAREST.takeIf { here != null },
            ListSortUiState.STRONGEST
        )
        // Without a location, "nearest" can't be computed; fall back rather than show an unsorted list.
        val sort = requested.takeIf { it in sortOptions } ?: ListSortUiState.RECENT
        ListUiState(
            period = currentPeriod,
            content = feedState.toFeedContent(clock.instant(), here) { it.sortedWith(sort.comparator) },
            location = location.toUiState(),
            sort = sort,
            sortOptions = sortOptions,
            isRefreshing = refreshing
        )
    }.stateIn(
        scope = viewModelScope,
        started = WhileUiSubscribed,
        initialValue = ListUiState()
    )

    fun onEvent(event: ListUiEvent) {
        when (event) {
            is ListUiEvent.SelectPeriod -> viewModelScope.launch {
                preferencesRepository.setSelectedPeriod(event.period)
            }

            is ListUiEvent.SelectSort -> requestedSort.value = event.sort

            is ListUiEvent.Refresh -> refresh()

            is ListUiEvent.CheckLocation -> viewModelScope.launch { locationRepository.refresh() }
        }
    }

    private fun refresh() {
        // Claim the flag before suspending so a second pull can't start a parallel refresh.
        if (isRefreshing.value) return
        isRefreshing.value = true
        viewModelScope.launch {
            try {
                earthquakeRepository.refresh(period.first())
            } finally {
                isRefreshing.value = false
            }
        }
    }
}

private val ListSortUiState.comparator: Comparator<EarthquakeUiState>
    get() = when (this) {
        ListSortUiState.RECENT -> compareBy { it.minutesAgo }
        ListSortUiState.NEAREST -> compareBy(nullsLast<Int>()) { it.distanceKm }
        ListSortUiState.STRONGEST -> compareByDescending { it.magnitude }
    }
