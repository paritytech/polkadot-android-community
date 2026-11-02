package io.paritytech.polkadotapp.feature_chats_impl.domain.models

import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId

/**
 * A message that is handled when it arrives and is never saved to the chat history.
 */
sealed interface TransientMessage {
    /** The contact has seen our messages up to [messageId]. */
    class Seen(val messageId: ChatMessageId) : TransientMessage
}
