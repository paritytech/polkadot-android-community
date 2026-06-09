package io.paritytech.polkadotapp.feature_wallet_impl.presentation.sendPayment

import io.paritytech.polkadotapp.common.presentation.loading.LoadingState
import io.paritytech.polkadotapp.design.components.avatar.AvatarUiModel
import io.paritytech.polkadotapp.feature_account_api.presentation.address.model.ExtractedAddress
import kotlinx.collections.immutable.ImmutableList

data class PaymentSearchResultUiModel(
    val extractedAddress: ExtractedAddress,
    val avatarModel: AvatarUiModel,
)

data class SendPaymentUiState(
    val input: String = "",
    val loadingState: LoadingState<ImmutableList<PaymentSearchResultUiModel>> = LoadingState.Loading,
)
