package io.paritytech.polkadotapp.feature_dotns_impl.data.contract.abi

import io.novasama.substrate_sdk_android.extensions.fromHex
import io.novasama.substrate_sdk_android.extensions.toHexString
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Bytes32

object EvmContractCaller {
    @Suppress("UNCHECKED_CAST")
    private val contenthashOutputParams =
        listOf(object : TypeReference<DynamicBytes>() {}) as List<TypeReference<Type<*>>>

    @Suppress("UNCHECKED_CAST")
    private val textOutputParams =
        listOf(object : TypeReference<Utf8String>() {}) as List<TypeReference<Type<*>>>

    fun encodeContentHash(node: ByteArray): ByteArray {
        val function = Function(
            "contenthash",
            listOf(Bytes32(node)),
            contenthashOutputParams
        )
        return FunctionEncoder.encode(function).fromHex()
    }

    fun decodeContentHash(output: ByteArray): ByteArray? {
        val outputHex = output.toHexString(withPrefix = true)
        val decoded = FunctionReturnDecoder.decode(outputHex, contenthashOutputParams)
        if (decoded.isEmpty()) return null
        val bytes = (decoded[0] as DynamicBytes).value
        return if (bytes.isEmpty()) null else bytes
    }

    fun encodeText(node: ByteArray, key: String): ByteArray {
        val function = Function(
            "text",
            listOf(Bytes32(node), Utf8String(key)),
            textOutputParams
        )
        return FunctionEncoder.encode(function).fromHex()
    }

    fun decodeText(output: ByteArray): String? {
        val outputHex = output.toHexString(withPrefix = true)
        val decoded = FunctionReturnDecoder.decode(outputHex, textOutputParams)
        if (decoded.isEmpty()) return null
        val text = (decoded[0] as Utf8String).value
        return text.ifEmpty { null }
    }
}
