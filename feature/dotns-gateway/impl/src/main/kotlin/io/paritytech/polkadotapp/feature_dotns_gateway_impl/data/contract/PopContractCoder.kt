package io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.contract

import io.novasama.substrate_sdk_android.extensions.fromHex
import io.novasama.substrate_sdk_android.extensions.toHexString
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.bouncycastle.jcajce.provider.digest.Keccak
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.generated.Uint64
import java.math.BigInteger

object PopContractCoder {
    class ReservationQueueBounds(val head: BigInteger, val tail: BigInteger)

    class ReservationEntry(val owner: ByteArray, val joinedAt: BigInteger)

    @Suppress("UNCHECKED_CAST")
    private val reservationMetaOutputParams = listOf(
        object : TypeReference<Uint64>() {},
        object : TypeReference<Uint64>() {}
    ) as List<TypeReference<Type<*>>>

    @Suppress("UNCHECKED_CAST")
    private val reservationDurationOutputParams =
        listOf(object : TypeReference<Uint64>() {}) as List<TypeReference<Type<*>>>

    @Suppress("UNCHECKED_CAST")
    private val maxQueueStepsPerCallOutputParams =
        listOf(object : TypeReference<Uint256>() {}) as List<TypeReference<Type<*>>>

    @Suppress("UNCHECKED_CAST")
    private val reservationEntryOutputParams = listOf(
        object : TypeReference<Address>() {},
        object : TypeReference<Uint64>() {}
    ) as List<TypeReference<Type<*>>>

    @Suppress("UNCHECKED_CAST")
    private val chatKeyOutputParams =
        listOf(object : TypeReference<DynamicBytes>() {}) as List<TypeReference<Type<*>>>

    fun reservationLabelHash(baseLabel: String): ByteArray {
        return Keccak.Digest256().digest(baseLabel.toByteArray(Charsets.UTF_8))
    }

    fun encodeReservationMeta(labelHash: ByteArray): ByteArray {
        val function = Function("reservationMeta", listOf(Bytes32(labelHash)), reservationMetaOutputParams)
        return FunctionEncoder.encode(function).fromHex()
    }

    fun decodeReservationMeta(output: ByteArray): ReservationQueueBounds? {
        val decoded = FunctionReturnDecoder.decode(output.toHexString(withPrefix = true), reservationMetaOutputParams)
        if (decoded.size < 2) return null
        return ReservationQueueBounds(head = (decoded[0] as Uint64).value, tail = (decoded[1] as Uint64).value)
    }

    fun encodeReservationDuration(): ByteArray {
        val function = Function("reservationDuration", emptyList(), reservationDurationOutputParams)
        return FunctionEncoder.encode(function).fromHex()
    }

    fun decodeReservationDuration(output: ByteArray): BigInteger? {
        val decoded = FunctionReturnDecoder.decode(output.toHexString(withPrefix = true), reservationDurationOutputParams)
        return (decoded.firstOrNull() as Uint64?)?.value
    }

    fun encodeMaxQueueStepsPerCall(): ByteArray {
        val function = Function("MAX_QUEUE_STEPS_PER_CALL", emptyList(), maxQueueStepsPerCallOutputParams)
        return FunctionEncoder.encode(function).fromHex()
    }

    fun decodeMaxQueueStepsPerCall(output: ByteArray): BigInteger? {
        val decoded = FunctionReturnDecoder.decode(output.toHexString(withPrefix = true), maxQueueStepsPerCallOutputParams)
        return (decoded.firstOrNull() as Uint256?)?.value
    }

    fun encodeExpireReservation(baseLabel: String): ByteArray {
        val function = Function("expireReservation", listOf(Utf8String(baseLabel)), emptyList())
        return FunctionEncoder.encode(function).fromHex()
    }

    fun encodeReservationEntry(labelHash: ByteArray, index: BigInteger): ByteArray {
        val function = Function("reservationEntry", listOf(Bytes32(labelHash), Uint64(index)), reservationEntryOutputParams)
        return FunctionEncoder.encode(function).fromHex()
    }

    fun decodeReservationEntry(output: ByteArray): ReservationEntry? {
        val decoded = FunctionReturnDecoder.decode(output.toHexString(withPrefix = true), reservationEntryOutputParams)
        if (decoded.size < 2) return null
        return ReservationEntry(owner = (decoded[0] as Address).value.fromHex(), joinedAt = (decoded[1] as Uint64).value)
    }

    fun encodeChatKey(node: ByteArray): ByteArray {
        val function = Function("chatKey", listOf(Bytes32(node)), chatKeyOutputParams)
        return FunctionEncoder.encode(function).fromHex()
    }

    fun decodeChatKey(output: ByteArray): ByteArray? {
        val decoded = FunctionReturnDecoder.decode(output.toHexString(withPrefix = true), chatKeyOutputParams)
        if (decoded.isEmpty()) return null
        val bytes = (decoded[0] as DynamicBytes).value
        return if (bytes.isEmpty()) null else bytes
    }
}
