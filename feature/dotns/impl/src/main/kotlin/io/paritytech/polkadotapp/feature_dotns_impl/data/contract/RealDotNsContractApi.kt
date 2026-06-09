package io.paritytech.polkadotapp.feature_dotns_impl.data.contract

import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.feature_dotns_impl.data.config.DotNsConfigProvider
import io.paritytech.polkadotapp.feature_dotns_impl.data.contract.abi.EvmContractCaller
import io.paritytech.polkadotapp.feature_dotns_impl.data.contract.abi.NameHash
import io.paritytech.polkadotapp.feature_revive_api.ReviveContractApi
import javax.inject.Inject

class RealDotNsContractApi @Inject constructor(
    private val reviveContractApi: ReviveContractApi,
    private val chainRegistry: ChainRegistry,
    private val dotNsConfigProvider: DotNsConfigProvider,
) : DotNsContractApi {
    override suspend fun resolveContentHash(dotNsName: String): Result<ByteArray?> {
        val node = NameHash.nameHash(dotNsName)
        val callData = EvmContractCaller.encodeContentHash(node)

        return callContract(callData).map { outputBytes ->
            val contentHash = if (outputBytes.isEmpty()) null else EvmContractCaller.decodeContentHash(outputBytes)
            contentHash?.let(::stripEip1577Prefix)
        }
    }

    override suspend fun getMetadata(dotNsName: String, key: String): Result<String?> {
        val node = NameHash.nameHash(dotNsName)
        val callData = EvmContractCaller.encodeText(node, key)

        return callContract(callData).map { outputBytes ->
            if (outputBytes.isEmpty()) null else EvmContractCaller.decodeText(outputBytes)
        }
    }

    private suspend fun callContract(inputData: ByteArray): Result<ByteArray> {
        return dotNsConfigProvider.getDotNsConfig().flatMap { config ->
            reviveContractApi.callReadOnly(
                chainId = chainRegistry.knownChains.assetHub,
                contract = config.resolverContractAddress,
                input = inputData.toDataByteArray(),
            ).map { it.value }
        }
    }

    /**
     * Strips the EIP-1577 uvarint-encoded multicodec prefix from a content hash.
     *
     * The `contenthash()` resolver returns an EIP-1577 encoded value where the first bytes
     * identify the storage system. IPFS namespace (0xe3) is uvarint-encoded as 2 bytes: `e3 01`.
     * The remaining bytes are the raw CID.
     */
    private fun stripEip1577Prefix(contentHash: ByteArray): ByteArray {
        require(contentHash.size > EIP_1577_IPFS_PREFIX.size) { "Content hash too short" }

        val prefix = contentHash.copyOfRange(0, EIP_1577_IPFS_PREFIX.size)
        require(prefix.contentEquals(EIP_1577_IPFS_PREFIX)) {
            "Unsupported EIP-1577 prefix: 0x${prefix.joinToString("") { "%02x".format(it) }}"
        }

        return contentHash.copyOfRange(EIP_1577_IPFS_PREFIX.size, contentHash.size)
    }

    companion object {
        private val EIP_1577_IPFS_PREFIX = byteArrayOf(0xe3.toByte(), 0x01)
    }
}
