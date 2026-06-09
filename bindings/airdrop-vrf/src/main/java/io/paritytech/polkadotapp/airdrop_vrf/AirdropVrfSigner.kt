package io.paritytech.polkadotapp.airdrop_vrf

import io.paritytech.polkadotapp.common.utils.mapError

/** The sr25519 airdrop VRF output: the 32-byte pre-output (the lottery seed) and the 64-byte proof. */
class AirdropVrfSignature(val preOutput: ByteArray, val proof: ByteArray)

sealed class AirdropVrfError(message: String) : Exception(message) {
    object InvalidKeypairLength : AirdropVrfError("airdrop VRF keypair must be $KEYPAIR_SIZE bytes")
    object InvalidEventIdLength : AirdropVrfError("airdrop VRF event id must be $EVENT_ID_SIZE bytes")
    class SigningFailed(cause: Throwable) : AirdropVrfError("airdrop VRF signing failed: ${cause.message}")
}

/**
 * Kotlin boundary over the native [AirdropVrfCrypto]. Validates the input lengths and turns the
 * native call (which throws on failure) into a [Result]. The transcript is built natively to match
 * the runtime — see the Rust crate.
 */
object AirdropVrfSigner {
    /**
     * @param keypair 96 bytes — the candidate wallet sr25519 key as rawSecretKey(64) ++ rawPublicKey(32).
     * @param eventId 32 bytes — the airdrop event id for the game.
     */
    fun sign(keypair: ByteArray, eventId: ByteArray): Result<AirdropVrfSignature> {
        if (keypair.size != KEYPAIR_SIZE) return Result.failure(AirdropVrfError.InvalidKeypairLength)
        if (eventId.size != EVENT_ID_SIZE) return Result.failure(AirdropVrfError.InvalidEventIdLength)

        return runCatching { AirdropVrfCrypto.sign(keypair, eventId) }
            .mapCatching { output ->
                AirdropVrfSignature(
                    preOutput = output.copyOfRange(0, PRE_OUTPUT_SIZE),
                    proof = output.copyOfRange(PRE_OUTPUT_SIZE, OUTPUT_SIZE),
                )
            }
            .mapError { AirdropVrfError.SigningFailed(it) }
    }
}

private const val KEYPAIR_SIZE = 96
private const val EVENT_ID_SIZE = 32
private const val PRE_OUTPUT_SIZE = 32
private const val OUTPUT_SIZE = 96
