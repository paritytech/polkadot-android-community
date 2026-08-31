package io.paritytech.polkadotapp.tools_integrity_impl.data.claim

import android.media.MediaDrm
import android.media.UnsupportedSchemeException
import java.util.UUID

internal class WidevineUnavailableException(
    message: String,
    cause: Throwable?
) : Exception(message, cause)

internal object WidevineEvidenceReader {
    private val WIDEVINE_UUID = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")

    private const val SECURITY_LEVEL_PROPERTY = "securityLevel"
    private const val SECURITY_LEVEL_L1 = "L1"
    private const val MIN_DEVICE_ID_BYTES = 1
    private const val MAX_DEVICE_ID_BYTES = 64

    fun readL1DeviceId(): ByteArray? {
        val drm = try {
            MediaDrm(WIDEVINE_UUID)
        } catch (error: UnsupportedSchemeException) {
            return null
        } catch (error: Exception) {
            throw WidevineUnavailableException("Widevine MediaDrm could not be opened", error)
        }
        return drm.use {
            val securityLevel = try {
                drm.getPropertyString(SECURITY_LEVEL_PROPERTY)
            } catch (error: Exception) {
                throw WidevineUnavailableException("Widevine security level unavailable", error)
            }
            if (!isL1SecurityLevel(securityLevel)) return@use null

            val deviceId = try {
                drm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
            } catch (error: Exception) {
                throw WidevineUnavailableException("Widevine device id unavailable", error)
            }
            if (deviceId.size !in MIN_DEVICE_ID_BYTES..MAX_DEVICE_ID_BYTES) {
                throw WidevineUnavailableException(
                    "Widevine device id must be $MIN_DEVICE_ID_BYTES..$MAX_DEVICE_ID_BYTES bytes, got ${deviceId.size}",
                    null
                )
            }
            deviceId
        }
    }

    internal fun isL1SecurityLevel(securityLevel: String): Boolean = securityLevel == SECURITY_LEVEL_L1
}
