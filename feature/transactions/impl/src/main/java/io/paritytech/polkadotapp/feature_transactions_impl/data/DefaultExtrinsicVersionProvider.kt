package io.paritytech.polkadotapp.feature_transactions_impl.data

import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.feature_transactions.api.data.ExtrinsicVersion
import javax.inject.Inject

interface DefaultExtrinsicVersionProvider {
    suspend fun getDefaultExtrinsicVersion(chainId: ChainId, isSigned: Boolean): ExtrinsicVersion
}

class RealDefaultExtrinsicVersionProvider @Inject constructor(
    private val knownChains: KnownChains,
) : DefaultExtrinsicVersionProvider {
    override suspend fun getDefaultExtrinsicVersion(chainId: ChainId, isSigned: Boolean): ExtrinsicVersion {
        return when (chainId) {
            knownChains.people -> ExtrinsicVersion.V5
            knownChains.assetHub -> if (isSigned) ExtrinsicVersion.V4 else ExtrinsicVersion.V5
            else -> ExtrinsicVersion.V4
        }
    }
}
