package io.paritytech.polkadotapp.feature_chats_impl.domain.models

/**
 * The newest incoming message named in a Seen that the contact acknowledged.
 */
data class LastSentSeen(
    val sortOrder: Long,
    val timestamp: Long,
)
