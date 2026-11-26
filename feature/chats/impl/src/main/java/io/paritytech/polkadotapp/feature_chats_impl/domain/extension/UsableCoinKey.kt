package io.paritytech.polkadotapp.feature_chats_impl.domain.extension

import io.novasama.substrate_sdk_android.encrypt.keypair.substrate.Sr25519Keypair
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.intoAccountId
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage.Content.CoinagePayment
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinPrivateKey
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.deriveKeypairOrNull

internal class UsableCoinKey(val key: CoinPrivateKey, val keypair: Sr25519Keypair) {
    val accountId: AccountId
        get() = keypair.publicKey.intoAccountId()
}

internal fun CoinagePayment.toUsableCoinKeys(): List<UsableCoinKey> = coinKeys.mapNotNull { bytes ->
    val key = bytes.toDataByteArray()
    key.deriveKeypairOrNull()?.let { keypair -> UsableCoinKey(key, keypair) }
}
