package io.paritytech.polkadotapp.feature_coinage_impl.domain.transaction.submission

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.decodeFromByteArrayCatching
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinPrivateKey
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.SubmissionPolicy
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToByteArray
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

const val COINAGE_SPLIT_POLICY_ID = "coinage-split"
const val COINAGE_UNLOAD_POLICY_ID = "coinage-unload"
const val COINAGE_CLAIM_POLICY_ID = "coinage-claim"

/**
 * What a transfer's policy needs beyond the ledger: how long a rebuild may still be attempted. A null
 * [retryUntil] builds the transfer once, and a failure of that attempt is final.
 */
@OptIn(ExperimentalTime::class)
data class TransferSubmissionParams(val retryUntil: Instant?)

/** What a claim's policy needs beyond the ledger: the peer's key, which only the payment message carries. */
@OptIn(ExperimentalTime::class)
data class ClaimRetryParams(
    val retryUntil: Instant,
    val receivedKey: CoinPrivateKey,
)

/**
 * The persisted shape of the policies' parameters. Stored with every scheduled transaction, so a change to
 * either shape needs a versioned decoder for the rows already written.
 */
@OptIn(ExperimentalTime::class)
object CoinageSubmissionParams {
    fun splitPolicy(params: TransferSubmissionParams) = SubmissionPolicy(COINAGE_SPLIT_POLICY_ID, params.encode())

    fun unloadPolicy(params: TransferSubmissionParams) = SubmissionPolicy(COINAGE_UNLOAD_POLICY_ID, params.encode())

    fun claimPolicy(params: ClaimRetryParams) = SubmissionPolicy(
        id = COINAGE_CLAIM_POLICY_ID,
        params = BinaryScale.encodeToByteArray(
            ClaimRetryParamsScale(params.retryUntil.toEpochMilliseconds(), params.receivedKey)
        ).toDataByteArray(),
    )

    fun decodeTransfer(params: DataByteArray): Result<TransferSubmissionParams> =
        BinaryScale.decodeFromByteArrayCatching<TransferSubmissionParamsScale>(params.value).map { scale ->
            TransferSubmissionParams(scale.retryUntilMillis?.let(Instant::fromEpochMilliseconds))
        }

    fun decodeClaim(params: DataByteArray): Result<ClaimRetryParams> =
        BinaryScale.decodeFromByteArrayCatching<ClaimRetryParamsScale>(params.value).map { scale ->
            ClaimRetryParams(Instant.fromEpochMilliseconds(scale.retryUntilMillis), scale.receivedKey)
        }

    /** Only a transfer scheduled with a retry window is built again. */
    fun hasRetryWindow(params: DataByteArray): Boolean = decodeTransfer(params).getOrNull()?.retryUntil != null

    private fun TransferSubmissionParams.encode() =
        BinaryScale.encodeToByteArray(TransferSubmissionParamsScale(retryUntil?.toEpochMilliseconds())).toDataByteArray()
}

@Serializable
private class TransferSubmissionParamsScale(
    val retryUntilMillis: Long?,
)

@Serializable
private class ClaimRetryParamsScale(
    val retryUntilMillis: Long,
    val receivedKey: DataByteArray,
)
