package io.paritytech.polkadotapp.common.presentation.search

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

@Immutable
sealed interface SearchState<out T> {
    data object Initial : SearchState<Nothing>
    data object Loading : SearchState<Nothing>
    data object Empty : SearchState<Nothing>
    data class Error(val exception: Throwable) : SearchState<Nothing>
    data class Loaded<T>(val results: ImmutableList<T>) : SearchState<T>
}
