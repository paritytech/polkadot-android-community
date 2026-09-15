package io.paritytech.polkadotapp.chains.multiNetwork

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.util.Ids
import io.paritytech.polkadotapp.common.data.network.TestnetEnvironment

class KnownChains(
    val people: ChainId,
    val assetHub: ChainId,
    val bulletIn: ChainId,
    val hydration: ChainId?,
    /**
     * dotNS TLD of the network the chains above belong to, passed to the TrUAPI core as
     * `HostRuntimeConfig.networkSuffix`. The wallet's reserved identities are derived under it, so
     * it has to name the same network the chains do: a wrong value derives a different person from
     * the same seed rather than failing.
     *
     * It lives beside the chain selection, chosen by the same environment, so the two cannot drift
     * apart.
     */
    val networkSuffix: String,
) {
    companion object {
        fun createFor(environment: TestnetEnvironment): KnownChains {
            return when (environment) {
                TestnetEnvironment.TESTNET -> KnownChains(
                    people = Chain.Ids.PREVIEWNET_PEOPLE,
                    assetHub = Chain.Ids.PREVIEWNET_ASSET_HUB,
                    bulletIn = Chain.Ids.PREVIEWNET_BULLET_IN,
                    hydration = null,
                    networkSuffix = "testnet"
                )

                TestnetEnvironment.NIGHTLY -> KnownChains(
                    people = Chain.Ids.NIGHTLY_PEOPLE,
                    assetHub = Chain.Ids.NIGHTLY_ASSET_HUB,
                    bulletIn = Chain.Ids.NIGHTLY_BULLET_IN,
                    hydration = null,
                    networkSuffix = "paseo"
                )

                TestnetEnvironment.PRODUCTION -> KnownChains(
                    people = Chain.Ids.RELEASE_PEOPLE,
                    assetHub = Chain.Ids.RELEASE_ASSET_HUB,
                    bulletIn = Chain.Ids.RELEASE_BULLETIN,
                    hydration = null,
                    // Unconfirmed, tracked by
                    // https://github.com/paritytech/host-rust-core/issues/760. The Release chains
                    // carry the same assets as the nightly ones, which points at Paseo rather than
                    // Polkadot.
                    networkSuffix = "paseo"
                )
            }
        }
    }
}
