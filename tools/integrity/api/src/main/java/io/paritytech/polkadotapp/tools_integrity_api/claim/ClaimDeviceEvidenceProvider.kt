package io.paritytech.polkadotapp.tools_integrity_api.claim

interface ClaimDeviceEvidenceProvider {

    /**
     * Collects fresh Widevine device evidence for one username claim
     * (evidence wire spec `dub/poud/android/v1`). Success with `null` means
     * this device does not attest (emulator, or Widevine L1 not measured);
     * failure means the claim must be aborted so it stays retryable —
     * evidence is never fabricated.
     */
    suspend fun collectEvidence(): Result<ClaimDeviceEvidence?>
}

/** Base64 wire fields of `POST /api/v1/usernames`, sent together or not at all. */
class ClaimDeviceEvidence(
    val attestationChain: List<String>,
    val deviceChallenge: String,
    val deviceId: String
)
