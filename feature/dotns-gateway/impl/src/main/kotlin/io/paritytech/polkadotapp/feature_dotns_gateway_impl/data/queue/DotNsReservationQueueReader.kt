package io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.queue

import io.paritytech.polkadotapp.chains.di.RemoteSourceQualifier
import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.chains.storage.source.StorageDataSource
import io.paritytech.polkadotapp.chains.storage.source.query.api.queryNonNull
import io.paritytech.polkadotapp.chains.storage.source.query.metadata
import io.paritytech.polkadotapp.chains.storage.source.queryCatching
import io.paritytech.polkadotapp.chains.storage.typed.now
import io.paritytech.polkadotapp.chains.storage.typed.timestamp
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.config.DotNsGatewayConfig
import io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.contract.PopContractCoder
import io.paritytech.polkadotapp.feature_revive_api.ReviveContractApi
import io.paritytech.polkadotapp.feature_revive_api.ReviveContractReverted
import java.math.BigInteger
import javax.inject.Inject

class DotNsReservationQueue(
    val firstActiveHolder: ByteArray?,
    val deadInFront: Int
)

class DotNsReservationQueueReader @Inject constructor(
    @param:RemoteSourceQualifier private val remoteStorageSource: StorageDataSource,
    private val reviveContractApi: ReviveContractApi,
    private val knownChains: KnownChains
) {
    // The front entry can be expired or emptied while a later one is still active: that user holds the name.
    suspend fun read(baseLabel: String, config: DotNsGatewayConfig): Result<DotNsReservationQueue> {
        val labelHash = PopContractCoder.reservationLabelHash(baseLabel)

        return readController(config, PopContractCoder.encodeReservationMeta(labelHash)) { output ->
            PopContractCoder.decodeReservationMeta(output) ?: error("Malformed reservationMeta output for $baseLabel")
        }.flatMap { bounds ->
            if (bounds.head >= bounds.tail) {
                Result.success(DotNsReservationQueue(firstActiveHolder = null, deadInFront = 0))
            } else {
                readController(config, PopContractCoder.encodeReservationDuration()) { output ->
                    PopContractCoder.decodeReservationDuration(output) ?: error("Malformed reservationDuration output")
                }.flatMap { duration ->
                    chainNowSeconds().flatMap { now ->
                        scanFrom(labelHash, bounds.head, bounds.tail, duration, now, deadInFront = 0, config)
                    }
                }
            }
        }
    }

    // Null on a controller without the getter: that release clears every dead entry inside the gateway call.
    suspend fun maxQueueStepsPerCall(config: DotNsGatewayConfig): Result<BigInteger?> {
        return reviveContractApi.callReadOnly(
            chainId = knownChains.assetHub,
            contract = config.popControllerAddress,
            input = PopContractCoder.encodeMaxQueueStepsPerCall().toDataByteArray()
        ).fold(
            onSuccess = { output ->
                runCatching { PopContractCoder.decodeMaxQueueStepsPerCall(output.value) ?: error("Malformed MAX_QUEUE_STEPS_PER_CALL output") }
            },
            onFailure = { error -> if (error is ReviveContractReverted) Result.success(null) else Result.failure(error) }
        )
    }

    private suspend fun scanFrom(
        labelHash: ByteArray,
        index: BigInteger,
        tail: BigInteger,
        duration: BigInteger,
        now: BigInteger,
        deadInFront: Int,
        config: DotNsGatewayConfig
    ): Result<DotNsReservationQueue> {
        if (index >= tail) return Result.success(DotNsReservationQueue(firstActiveHolder = null, deadInFront = deadInFront))

        return readController(config, PopContractCoder.encodeReservationEntry(labelHash, index)) { output ->
            PopContractCoder.decodeReservationEntry(output) ?: error("Malformed reservationEntry output at $index")
        }.flatMap { entry ->
            val active = entry.owner.any { it != 0.toByte() } && entry.joinedAt + duration >= now
            if (active) {
                Result.success(DotNsReservationQueue(firstActiveHolder = entry.owner, deadInFront = deadInFront))
            } else {
                scanFrom(labelHash, index + BigInteger.ONE, tail, duration, now, deadInFront + 1, config)
            }
        }
    }

    private suspend fun chainNowSeconds(): Result<BigInteger> {
        return remoteStorageSource.queryCatching(knownChains.assetHub) {
            metadata.timestamp.now.queryNonNull()
        }.map { millis -> millis / BigInteger.valueOf(1000) }
    }

    private suspend fun <T> readController(
        config: DotNsGatewayConfig,
        input: ByteArray,
        decode: (ByteArray) -> T
    ): Result<T> {
        return reviveContractApi.callReadOnly(
            chainId = knownChains.assetHub,
            contract = config.popControllerAddress,
            input = input.toDataByteArray()
        ).mapCatching { output -> decode(output.value) }
    }
}
