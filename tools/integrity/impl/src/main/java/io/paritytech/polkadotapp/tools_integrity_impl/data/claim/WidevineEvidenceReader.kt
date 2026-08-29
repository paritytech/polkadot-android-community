package io.paritytech.polkadotapp.tools_integrity_impl.data.claim

import android.media.MediaDrm
import android.media.NotProvisionedException
import android.media.ResourceBusyException
import android.media.UnsupportedSchemeException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

internal const val WIDEVINE_LEVEL_L1 = 1
internal const val WIDEVINE_LEVEL_L3 = 3

internal class WidevineEvidence(
    val deviceId: ByteArray,
    val level: Int
)

internal class WidevineUnavailableException(
    message: String,
    cause: Throwable?
) : Exception(message, cause)

internal object WidevineEvidenceReader {
    private val WIDEVINE_UUID = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")

    private const val MIN_DEVICE_ID_BYTES = 1
    private const val MAX_DEVICE_ID_BYTES = 64
    private const val PROVISION_TIMEOUT_MS = 30_000

    fun read(): WidevineEvidence {
        val drm = try {
            MediaDrm(WIDEVINE_UUID)
        } catch (error: UnsupportedSchemeException) {
            throw WidevineUnavailableException("Widevine MediaDrm could not be opened", error)
        }
        return drm.use {
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
            WidevineEvidence(
                deviceId = deviceId,
                level = measureSecurityLevel(
                    openSession = { drm.openSession(MediaDrm.SECURITY_LEVEL_HW_SECURE_ALL) },
                    closeSession = drm::closeSession,
                    provision = { provision(drm) }
                )
            )
        }
    }

    internal fun measureSecurityLevel(
        openSession: () -> ByteArray,
        closeSession: (ByteArray) -> Unit,
        provision: () -> Unit
    ): Int {
        for (attempt in 0..1) {
            val session = try {
                openSession()
            } catch (error: NotProvisionedException) {
                if (attempt == 1) {
                    throw WidevineUnavailableException("Widevine is not provisioned after provisioning", error)
                }
                try {
                    provision()
                } catch (provisionError: Exception) {
                    throw WidevineUnavailableException("Widevine provisioning failed", provisionError)
                }
                continue
            } catch (error: ResourceBusyException) {
                throw WidevineUnavailableException("Widevine sessions are busy", error)
            } catch (error: IllegalArgumentException) {
                // HW_SECURE_ALL above the device's maximum level is a real measurement
                // (GrapheneOS ships L3), not a failure.
                return WIDEVINE_LEVEL_L3
            }
            closeSession(session)
            return WIDEVINE_LEVEL_L1
        }
        error("Widevine measurement exhausted its retry")
    }

    private fun provision(drm: MediaDrm) {
        val request = drm.provisionRequest
        if (request.defaultUrl.isBlank()) {
            throw IOException("Widevine provisioning server URL is unavailable")
        }
        val response = postProvisionRequest(request.defaultUrl, request.data)
        drm.provideProvisionResponse(response)
    }

    private fun postProvisionRequest(url: String, requestData: ByteArray): ByteArray {
        val body = "{\"signedRequest\":\"".encodeToByteArray() + requestData + "\"}".encodeToByteArray()
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = PROVISION_TIMEOUT_MS
            connection.readTimeout = PROVISION_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { output -> output.write(body) }
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IOException("Widevine provisioning server returned HTTP $status")
            }
            return connection.inputStream.use { input -> input.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}
