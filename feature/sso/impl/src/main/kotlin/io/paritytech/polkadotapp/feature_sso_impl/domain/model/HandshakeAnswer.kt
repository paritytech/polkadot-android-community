package io.paritytech.polkadotapp.feature_sso_impl.domain.model

import io.paritytech.polkadotapp.common.domain.model.EncodedPublicKey

class HandshakeAnswer(
    val encryptedData: ByteArray,
    val tempSharedEncryptionPublicKey: EncodedPublicKey
)
