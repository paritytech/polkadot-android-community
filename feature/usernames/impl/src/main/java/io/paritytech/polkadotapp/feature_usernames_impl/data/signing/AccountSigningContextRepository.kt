package io.paritytech.polkadotapp.feature_usernames_impl.data.signing

import io.novasama.substrate_sdk_android.runtime.metadata.module.Module
import io.novasama.substrate_sdk_android.runtime.metadata.moduleOrNull
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.multiNetwork.withRuntime
import io.paritytech.polkadotapp.chains.storage.source.query.api.QueryableModule
import io.paritytech.polkadotapp.chains.storage.source.query.api.constantOrNull
import io.paritytech.polkadotapp.chains.util.Modules
import io.paritytech.polkadotapp.chains.util.WithRuntime
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_usernames_impl.domain.model.AccountSigningContexts
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

interface AccountSigningContextRepository {
    suspend fun getContexts(): Result<AccountSigningContexts>
}

class RealAccountSigningContextRepository @Inject constructor(
    private val chainRegistry: ChainRegistry,
    private val knownChains: KnownChains
) : AccountSigningContextRepository {
    override suspend fun getContexts(): Result<AccountSigningContexts> = runCancellableCatching {
        coroutineScope {
            val peopleLite = async { signingContext(knownChains.people, Modules.PEOPLE_LITE) }
            val dotNsGateway = async { signingContext(knownChains.assetHub, Modules.DOTNS_GATEWAY) }

            AccountSigningContexts(peopleLite = peopleLite.await(), dotNsGateway = dotNsGateway.await())
        }
    }

    private suspend fun signingContext(chainId: ChainId, palletName: String): DataByteArray? {
        return chainRegistry.withRuntime(chainId) {
            runtime.metadata.moduleOrNull(palletName)?.let(::VerifyingPallet)?.signingContext
        }
    }
}

@JvmInline
private value class VerifyingPallet(override val module: Module) : QueryableModule

context(withRuntime: WithRuntime)
private val VerifyingPallet.signingContext: DataByteArray?
    get() = constantOrNull("SigningContext")
