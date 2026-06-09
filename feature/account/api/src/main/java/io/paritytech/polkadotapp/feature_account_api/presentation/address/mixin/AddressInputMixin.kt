package io.paritytech.polkadotapp.feature_account_api.presentation.address.mixin

import io.paritytech.polkadotapp.common.presentation.loading.LoadingState
import io.paritytech.polkadotapp.common.presentation.ui.list.GroupedList
import io.paritytech.polkadotapp.common.presentation.ui.mixin.paste.PasteMixin
import io.paritytech.polkadotapp.feature_account_api.presentation.address.model.ExtractedAddress
import io.paritytech.polkadotapp.feature_account_api.presentation.address.model.ExtractedAddressesCategory
import io.paritytech.polkadotapp.feature_account_api.presentation.address.model.ExtractedAddressesSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

interface AddressInputMixin {
    interface AddressConverter {
        suspend fun convertToAddress(input: String): ExtractedAddressesSection
    }

    interface Factory {
        fun create(
            coroutineScope: CoroutineScope,
            converters: List<AddressConverter>,
        ): AddressInputMixin
    }

    val paste: PasteMixin

    val input: MutableStateFlow<String>

    val addressCandidates: Flow<LoadingState<GroupedList<ExtractedAddressesCategory, ExtractedAddress>>>
}
