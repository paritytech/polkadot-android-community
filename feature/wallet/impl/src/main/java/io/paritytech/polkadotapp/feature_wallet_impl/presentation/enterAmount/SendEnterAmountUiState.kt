package io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount

import androidx.compose.runtime.Immutable
import io.paritytech.polkadotapp.design.configs.colors.AvatarColorScheme
import io.paritytech.polkadotapp.feature_account_api.presentation.address.model.ExtractedAddress
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.TokenAmountModel

@Immutable
data class SendEnterAmountUiState(
    val input: String,
    val available: TokenAmountModel,
    val showBalanceError: Boolean,
    val recipient: String?,
    val recipientType: ExtractedAddress.DisplayType?,
    val recipientAvatarColor: AvatarColorScheme,
    val isSendInProgress: Boolean,
    val isSendEnabled: Boolean,
    val isAmountLocked: Boolean,
    val debugPlanInfo: SendPlanDebugInfo? = null,
)

sealed interface SendPlanDebugInfo {
    val strategyName: String
    val details: List<String>

    data class Coinage(
        override val strategyName: String,
        override val details: List<String>,
    ) : SendPlanDebugInfo

    data class External(
        override val strategyName: String,
        override val details: List<String>,
    ) : SendPlanDebugInfo
}
