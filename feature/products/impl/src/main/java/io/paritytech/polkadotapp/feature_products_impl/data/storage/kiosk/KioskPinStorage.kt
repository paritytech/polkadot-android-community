package io.paritytech.polkadotapp.feature_products_impl.data.storage.kiosk

import io.paritytech.polkadotapp.common.data.storage.preferences.encrypted.EncryptedPreferences
import javax.inject.Inject

interface KioskPinStorage {
    fun savePin(pin: String)

    fun readPin(): String?

    fun clearPin()
}

class RealKioskPinStorage @Inject constructor(
    private val encryptedPreferences: EncryptedPreferences,
) : KioskPinStorage {
    override fun savePin(pin: String) {
        encryptedPreferences.putEncryptedString(KIOSK_PIN_FIELD, pin)
    }

    override fun readPin(): String? = encryptedPreferences.getDecryptedString(KIOSK_PIN_FIELD)

    override fun clearPin() {
        encryptedPreferences.removeKey(KIOSK_PIN_FIELD)
    }

    private companion object {
        const val KIOSK_PIN_FIELD = "kiosk_pin_code"
    }
}
