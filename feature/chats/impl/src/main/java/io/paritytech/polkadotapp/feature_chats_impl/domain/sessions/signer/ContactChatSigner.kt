package io.paritytech.polkadotapp.feature_chats_impl.domain.sessions.signer

import io.novasama.substrate_sdk_android.encrypt.keypair.substrate.Sr25519Keypair

sealed interface ContactChatSigner {
    val keypair: Sr25519Keypair
    val kind: ChatSignerKind

    class Private(override val keypair: Sr25519Keypair) : ContactChatSigner {
        override val kind: ChatSignerKind = ChatSignerKind.PRIVATE
    }

    class Username(override val keypair: Sr25519Keypair) : ContactChatSigner {
        override val kind: ChatSignerKind = ChatSignerKind.USERNAME
    }
}

enum class ChatSignerKind { PRIVATE, USERNAME }
