package io.paritytech.polkadotapp.chains.extrinsic

import io.novasama.substrate_sdk_android.wsrpc.subscription.response.SubscriptionChange

sealed class ExtrinsicStatus(val terminal: Boolean) {
    sealed interface Failure

    sealed interface Submitted {
        val extrinsicHash: String
    }

    data class FailedToSubmit(val exception: Throwable) : ExtrinsicStatus(terminal = true), Failure

    data class Ready(override val extrinsicHash: String) : ExtrinsicStatus(terminal = false), Submitted

    data class Broadcast(override val extrinsicHash: String) : ExtrinsicStatus(terminal = false), Submitted

    data class InBlock(val blockHash: String, override val extrinsicHash: String) : ExtrinsicStatus(terminal = false), Submitted

    data class Finalized(val blockHash: String, override val extrinsicHash: String) : ExtrinsicStatus(terminal = true), Submitted

    data class Invalid(override val extrinsicHash: String) : ExtrinsicStatus(terminal = true), Submitted, Failure

    // rawStatus preserves the unmapped node status (dropped/usurped/retracted/future/…) for diagnostics.
    data class Other(val rawStatus: String, override val extrinsicHash: String) : ExtrinsicStatus(terminal = false), Submitted
}

private const val STATUS_READY = "ready"
private const val STATUS_BROADCAST = "broadcast"
private const val STATUS_IN_BLOCK = "inBlock"
private const val STATUS_FINALIZED = "finalized"
private const val STATUS_INVALID = "invalid"
private const val STATUS_FINALITY_TIMEOUT = "finalityTimeout"

fun SubscriptionChange.asExtrinsicStatus(extrinsicHash: String): ExtrinsicStatus {
    return when (val result = params.result) {
        STATUS_READY -> ExtrinsicStatus.Ready(extrinsicHash)
        STATUS_INVALID -> ExtrinsicStatus.Invalid(extrinsicHash)
        is Map<*, *> ->
            when {
                STATUS_BROADCAST in result -> ExtrinsicStatus.Broadcast(extrinsicHash)
                STATUS_IN_BLOCK in result -> ExtrinsicStatus.InBlock(extractBlockHash(result, STATUS_IN_BLOCK), extrinsicHash)
                STATUS_FINALIZED in result -> ExtrinsicStatus.Finalized(extractBlockHash(result, STATUS_FINALIZED), extrinsicHash)
                STATUS_FINALITY_TIMEOUT in result -> ExtrinsicStatus.Finalized(extractBlockHash(result, STATUS_FINALITY_TIMEOUT), extrinsicHash)
                else -> ExtrinsicStatus.Other(rawStatus = result.toString(), extrinsicHash = extrinsicHash)
            }
        else -> ExtrinsicStatus.Other(rawStatus = result.toString(), extrinsicHash = extrinsicHash)
    }
}

private fun extractBlockHash(
    map: Map<*, *>,
    key: String,
): String {
    return map[key] as? String ?: unknownStructure()
}

private fun unknownStructure(): Nothing = throw IllegalArgumentException("Unknown extrinsic status structure")
