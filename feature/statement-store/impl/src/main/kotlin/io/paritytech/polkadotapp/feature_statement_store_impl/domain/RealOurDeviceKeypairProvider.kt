package io.paritytech.polkadotapp.feature_statement_store_impl.domain

import io.novasama.substrate_sdk_android.extensions.fromHex
import io.novasama.substrate_sdk_android.extensions.toHexString
import io.paritytech.polkadotapp.common.data.storage.preferences.encrypted.EncryptedPreferences
import io.paritytech.polkadotapp.common.domain.model.EncodedPublicKey
import io.paritytech.polkadotapp.common.utils.Secp256r1KeyGenerator
import io.paritytech.polkadotapp.feature_statement_store_api.domain.OurDeviceKeypairProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.KeyPair
import javax.inject.Inject
import javax.inject.Singleton

private const val OUR_DEVICE_PRIVATE_KEY = "our_device_private_key"

@Singleton
class RealOurDeviceKeypairProvider @Inject constructor(
    private val encryptedPreferences: EncryptedPreferences,
    private val keyGenerator: Secp256r1KeyGenerator,
) : OurDeviceKeypairProvider {
    private val mutex = Mutex()

    @Volatile
    private var cached: KeyPair? = null

    override suspend fun get(): KeyPair {
        cached?.let { return it }

        return mutex.withLock {
            cached ?: load().also { cached = it }
        }
    }

    override suspend fun publicKey(): EncodedPublicKey {
        return keyGenerator.encode(get().public)
    }

    private fun load(): KeyPair {
        val storedPrivateKey = encryptedPreferences.getDecryptedString(OUR_DEVICE_PRIVATE_KEY)
        if (storedPrivateKey != null) {
            return keyGenerator.createKeyPair(storedPrivateKey.fromHex())
        }

        val fresh = keyGenerator.generateRandomKeypair()
        val privateKey = keyGenerator.encodePrivate(fresh.private)
        encryptedPreferences.putEncryptedString(OUR_DEVICE_PRIVATE_KEY, privateKey.value.toHexString())
        return fresh
    }
}
