@file:OptIn(ExperimentalStdlibApi::class)

package io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.contract

import io.novasama.substrate_sdk_android.extensions.fromHex
import org.bouncycastle.jcajce.provider.digest.Keccak
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.generated.Uint64
import java.math.BigInteger

class PopContractCoderTest {
    @Test
    fun `reservation queue reads use keccak-derived selectors and the label hash`() {
        val labelHash = PopContractCoder.reservationLabelHash("alice")
        val meta = PopContractCoder.encodeReservationMeta(labelHash)
        val entry = PopContractCoder.encodeReservationEntry(labelHash, BigInteger.valueOf(3))

        assertArrayEquals(Keccak.Digest256().digest("alice".toByteArray()), labelHash)
        assertArrayEquals(selectorOf("reservationMeta(bytes32)"), meta.copyOfRange(0, 4))
        assertArrayEquals(labelHash, meta.copyOfRange(4, 36))
        assertArrayEquals(selectorOf("reservationDuration()"), PopContractCoder.encodeReservationDuration())
        assertArrayEquals(selectorOf("reservationEntry(bytes32,uint64)"), entry.copyOfRange(0, 4))
        assertArrayEquals(labelHash, entry.copyOfRange(4, 36))
        assertEquals(BigInteger.valueOf(3), BigInteger(1, entry.copyOfRange(36, 68)))
    }

    @Test
    fun `encodeChatKey uses keccak-derived selector and node argument`() {
        val node = ByteArray(32) { it.toByte() }
        val encoded = PopContractCoder.encodeChatKey(node)

        assertArrayEquals(selectorOf("chatKey(bytes32)"), encoded.copyOfRange(0, 4))
        assertArrayEquals(node, encoded.copyOfRange(4, 36))
    }

    @Test
    fun `expireReservation and the step limit use keccak-derived selectors`() {
        val expire = PopContractCoder.encodeExpireReservation("alice")
        val output = abiEncodeReturnValues(listOf(Uint256(4)), listOf(object : TypeReference<Uint256>() {}))

        assertArrayEquals(selectorOf("expireReservation(string)"), expire.copyOfRange(0, 4))
        assertArrayEquals(selectorOf("MAX_QUEUE_STEPS_PER_CALL()"), PopContractCoder.encodeMaxQueueStepsPerCall())
        assertEquals(BigInteger.valueOf(4), PopContractCoder.decodeMaxQueueStepsPerCall(output))
    }

    @Test
    fun `decodeReservationMeta round-trips the queue bounds`() {
        val output = abiEncodeReturnValues(
            listOf(Uint64(4), Uint64(9)),
            listOf(object : TypeReference<Uint64>() {}, object : TypeReference<Uint64>() {})
        )

        val decoded = PopContractCoder.decodeReservationMeta(output)

        assertNotNull(decoded)
        assertEquals(BigInteger.valueOf(4), decoded!!.head)
        assertEquals(BigInteger.valueOf(9), decoded.tail)
    }

    @Test
    fun `decodeReservationEntry round-trips owner and join time`() {
        val ownerHex = "11".repeat(20)
        val output = abiEncodeReturnValues(
            listOf(Address("0x$ownerHex"), Uint64(1_700_000_000)),
            listOf(object : TypeReference<Address>() {}, object : TypeReference<Uint64>() {})
        )

        val decoded = PopContractCoder.decodeReservationEntry(output)

        assertNotNull(decoded)
        assertArrayEquals(ownerHex.fromHex(), decoded!!.owner)
        assertEquals(BigInteger.valueOf(1_700_000_000), decoded.joinedAt)
    }

    @Test
    fun `decodeChatKey round-trips key bytes`() {
        val key = ByteArray(65) { 7 }
        val output = abiEncodeReturnValues(
            listOf(DynamicBytes(key)),
            listOf(object : TypeReference<DynamicBytes>() {})
        )

        assertArrayEquals(key, PopContractCoder.decodeChatKey(output))
    }

    @Test
    fun `decodeChatKey returns null for empty bytes`() {
        val output = abiEncodeReturnValues(
            listOf(DynamicBytes(ByteArray(0))),
            listOf(object : TypeReference<DynamicBytes>() {})
        )

        assertNull(PopContractCoder.decodeChatKey(output))
    }

    @Test
    fun `decodeChatKey returns null for empty output`() {
        assertNull(PopContractCoder.decodeChatKey(ByteArray(0)))
    }

    private fun selectorOf(signature: String): ByteArray {
        return Keccak.Digest256().digest(signature.toByteArray()).copyOfRange(0, 4)
    }

    @Suppress("UNCHECKED_CAST")
    private fun abiEncodeReturnValues(values: List<Type<*>>, typeRefs: List<TypeReference<out Type<*>>>): ByteArray {
        val function = Function("_", values, typeRefs as List<TypeReference<Type<*>>>)
        val encoded = FunctionEncoder.encode(function).fromHex()
        return encoded.copyOfRange(4, encoded.size)
    }
}
