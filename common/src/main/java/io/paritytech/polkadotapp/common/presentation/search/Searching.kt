package io.paritytech.polkadotapp.common.presentation.search

import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val DEFAULT_SEARCH_DEBOUNCE = 300.milliseconds

/**
 * Drives a searching pipeline from a flow of optional inputs.
 *
 * - `null` input -> [SearchState.Initial].
 * - Non-null input -> emits [SearchState.Loading] immediately, waits [debounce], then runs [search].
 *   While the upstream keeps emitting, the in-flight delay/search is cancelled and restarted,
 *   so requests fire only after the input settles.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T : Any, R> Flow<T?>.withSearching(
    debounce: Duration = DEFAULT_SEARCH_DEBOUNCE,
    search: suspend (T) -> Result<List<R>>
): Flow<SearchState<R>> {
    return transformLatest { input ->
        if (input == null) {
            emit(SearchState.Initial)
        } else {
            emit(SearchState.Loading)
            delay(debounce)
            emit(search(input).toSearchState())
        }
    }
}

/**
 * Variant of [withSearching] for textual queries.
 *
 * Treats any input shorter than [minQueryLength] as "no input" -> emits [SearchState.Initial]
 * without invoking [search]. The default of 1 means an empty string is the only initial case.
 */
fun <R> Flow<String>.withQuerySearching(
    debounce: Duration = DEFAULT_SEARCH_DEBOUNCE,
    minQueryLength: Int = 1,
    search: suspend (String) -> Result<List<R>>
): Flow<SearchState<R>> {
    return map { it.takeIf { query -> query.length >= minQueryLength } }
        .withSearching(debounce, search)
}

private fun <R> Result<List<R>>.toSearchState(): SearchState<R> {
    return fold(
        onSuccess = { list ->
            if (list.isEmpty()) SearchState.Empty
            else SearchState.Loaded(list.toPersistentList())
        },
        onFailure = { SearchState.Error(it) }
    )
}
