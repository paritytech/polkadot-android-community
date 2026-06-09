package io.paritytech.polkadotapp.feature_become_citizen_impl.presentation.reserve.list.models

import androidx.compose.runtime.Immutable
import io.paritytech.polkadotapp.feature_become_citizen_api.presentation.common.TattooImage
import io.paritytech.polkadotapp.feature_become_citizen_impl.presentation.common.models.TattooFamilyUiIdentifier

@Immutable
data class TattooFamilyUiModel(
    val identifier: TattooFamilyUiIdentifier,
    val name: String,
    val totalCount: Int,
    val exampleTattoos: List<TattooImage>
)
