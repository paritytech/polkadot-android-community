package io.paritytech.polkadotapp.tools_integrity_impl.data.claim

import io.paritytech.polkadotapp.common.utils.sha256

// Both derivations are frozen wire contracts: changing the deviceId formula
// re-identifies the whole fleet, and the backend recomputes the attestation
// challenge from the JWT subject and the request fields.
internal object ClaimEvidenceDigests {
    private const val DOMAIN = "dub/poud/android/v1"
    private const val DEVICE_ID_DOMAIN = "dub/poud/widevine-id/v1"

    /** `SHA-256("dub/poud/widevine-id/v1" ‖ rawId)` — the raw id never leaves the device. */
    fun deviceId(rawWidevineId: ByteArray): ByteArray =
        (DEVICE_ID_DOMAIN.encodeToByteArray() + rawWidevineId).sha256()

    /** `SHA-256("dub/poud/android/v1" ‖ challenge ‖ candidate ‖ deviceId)` — the cert-bound hash. */
    fun attestationChallenge(challenge: ByteArray, candidate: ByteArray, deviceId: ByteArray): ByteArray {
        require(challenge.size == 32) { "challenge must be 32 bytes, got ${challenge.size}" }
        require(candidate.size == 32) { "candidate must be 32 bytes, got ${candidate.size}" }
        require(deviceId.size == 32) { "deviceId must be 32 bytes, got ${deviceId.size}" }
        return (DOMAIN.encodeToByteArray() + challenge + candidate + deviceId).sha256()
    }
}
