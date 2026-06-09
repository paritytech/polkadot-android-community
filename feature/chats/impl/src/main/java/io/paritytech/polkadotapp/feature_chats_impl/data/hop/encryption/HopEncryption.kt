package io.paritytech.polkadotapp.feature_chats_impl.data.hop.encryption

import io.paritytech.polkadotapp.common.data.encryption.MessageEncryption
import io.paritytech.polkadotapp.common.data.encryption.aes

class HopEncryption(aesKey: ByteArray) : MessageEncryption by MessageEncryption.aes(aesKey)
