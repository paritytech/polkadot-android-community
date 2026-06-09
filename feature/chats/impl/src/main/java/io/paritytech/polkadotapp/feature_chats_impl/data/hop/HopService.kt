package io.paritytech.polkadotapp.feature_chats_impl.data.hop

import io.novasama.substrate_sdk_android.extensions.fromHex
import io.novasama.substrate_sdk_android.extensions.toHexString
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.novasama.substrate_sdk_android.wsrpc.SocketService
import io.novasama.substrate_sdk_android.wsrpc.executeAsync
import io.novasama.substrate_sdk_android.wsrpc.mappers.nonNull
import io.novasama.substrate_sdk_android.wsrpc.mappers.pojo
import io.novasama.substrate_sdk_android.wsrpc.request.DeliveryType
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.model.HopAckRequest
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.model.HopClaimRequest
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.model.HopMultiSignature
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.model.HopMultiSigner
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.model.HopSubmitRequest
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.model.HopSubmitResult
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToHexString
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider

class HopService @Inject constructor(
    private val socketServiceProvider: Provider<SocketService>
) {
    class Session(private val socketService: SocketService) {
        suspend fun submit(
            data: ByteArray,
            recipients: List<HopMultiSigner>,
            signature: HopMultiSignature,
            signer: HopMultiSigner,
            submitTimestampMs: Long
        ): HopSubmitResult {
            val encodedRecipients = recipients.map { BinaryScale.encodeToHexString(it) }
            val encodedSignature = BinaryScale.encodeToByteArray(signature)
            val encodedSigner = BinaryScale.encodeToByteArray(signer)

            val request = HopSubmitRequest(
                data = data.toHexString(withPrefix = true),
                recipients = encodedRecipients,
                signature = encodedSignature.toHexString(withPrefix = true),
                signer = encodedSigner.toHexString(withPrefix = true),
                submitTimestamp = submitTimestampMs
            )

            return socketService.executeAsync(
                request = request,
                deliveryType = DeliveryType.AT_MOST_ONCE,
                mapper = pojo<HopSubmitResult>().nonNull()
            )
        }

        suspend fun claim(
            hash: ByteArray,
            signature: HopMultiSignature
        ): ByteArray {
            val encodedSignature = BinaryScale.encodeToByteArray(signature)

            val request = HopClaimRequest(
                hash = hash.toHexString(withPrefix = true),
                signature = encodedSignature.toHexString(withPrefix = true)
            )

            val hexResult = socketService.executeAsync(
                request = request,
                deliveryType = DeliveryType.AT_MOST_ONCE,
                mapper = pojo<String>().nonNull()
            )

            return hexResult.fromHex()
        }

        suspend fun ack(
            hash: ByteArray,
            signature: HopMultiSignature
        ) {
            val encodedSignature = BinaryScale.encodeToByteArray(signature)

            val request = HopAckRequest(
                hash = hash.toHexString(withPrefix = true),
                signature = encodedSignature.toHexString(withPrefix = true)
            )

            val response = socketService.executeAsync(
                request = request,
                deliveryType = DeliveryType.AT_MOST_ONCE
            )

            response.error?.let {
                error("hop_ack failed. Code: ${it.code}. Message: ${it.message}")
            }
        }
    }

    suspend fun <R> withSession(nodeUrl: String, block: suspend Session.() -> R): R {
        Timber.d("Starting Hop session with node $nodeUrl")
        val socketService = socketServiceProvider.get()
        socketService.start(nodeUrl, remainPaused = false)
        try {
            return block(Session(socketService))
        } finally {
            socketService.stop()
        }
    }
}
