package io.paritytech.polkadotapp.feature_account_impl.presentation.address.mixin

import io.paritytech.polkadotapp.common.presentation.ui.mixin.paste.PasteMixin
import io.paritytech.polkadotapp.common.utils.debounceIndexed
import io.paritytech.polkadotapp.common.utils.shareInBackground
import io.paritytech.polkadotapp.common.utils.withMapLoading
import io.paritytech.polkadotapp.feature_account_api.presentation.address.mixin.AddressInputMixin
import io.paritytech.polkadotapp.feature_account_api.presentation.address.model.ExtractedAddressesCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal class RealAddressInputMixin(
    private val pasteMixinFactory: PasteMixin.Factory,
    private val addressConverters: List<AddressInputMixin.AddressConverter>,
    private val coroutineScope: CoroutineScope,
) : AddressInputMixin,
    CoroutineScope by coroutineScope {
    override val paste: PasteMixin = pasteMixinFactory.create {
        input.value = it
    }

    override val input: MutableStateFlow<String> = MutableStateFlow("")

    override val addressCandidates = input
        .debounceIndexed { index, _ -> if (index == 0) Duration.ZERO else 300.milliseconds }
        .withMapLoading { convertInputToAddresses(it) }
        .shareInBackground()

    private suspend fun convertInputToAddresses(input: String) =
        runCatching {
            val allSections = addressConverters
                .mapNotNull {
                    runCatching {
                        it.convertToAddress(input)
                    }.getOrNull()
                }

            val (generalSections, customSections) = allSections.partition { it.category == ExtractedAddressesCategory.General }
            val allGeneralAddresses = generalSections.flatMap { it.addresses }

            buildMap {
                put(ExtractedAddressesCategory.General, allGeneralAddresses)

                customSections.forEach {
                    put(it.category, it.addresses)
                }
            }
        }
}
