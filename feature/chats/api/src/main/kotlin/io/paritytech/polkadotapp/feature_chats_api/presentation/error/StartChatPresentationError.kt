package io.paritytech.polkadotapp.feature_chats_api.presentation.error

import io.paritytech.polkadotapp.common.presentation.ui.errors.PresentationError
import io.paritytech.polkadotapp.common.presentation.ui.errors.StringResPresentationError
import io.paritytech.polkadotapp.common.presentation.ui.errors.UnexpectedPresentationError
import io.paritytech.polkadotapp.feature_chats_api.domain.error.StartChatError
import io.paritytech.polkadotapp.common.R as RCommon

// Lives in api rather than impl since chat starting is also driven from feature/wallet
fun StartChatError.toPresentationError(): PresentationError = when (this) {
    StartChatError.PeerNotRegistered -> StringResPresentationError(RCommon.string.chat_error_peer_not_available)

    is StartChatError.Unknown -> UnexpectedPresentationError()
}
