package io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.repository

import io.paritytech.polkadotapp.chains.di.LocalSourceQualifier
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.chains.storage.source.StorageDataSource
import io.paritytech.polkadotapp.chains.storage.source.query.metadata
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.EncodedPublicKey
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.coerceToUnit
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTldProvider
import io.paritytech.polkadotapp.feature_dotns_gateway_api.data.repository.DotNsGatewayRepository
import io.paritytech.polkadotapp.feature_dotns_gateway_api.domain.model.DotNsBaseNameAvailability
import io.paritytech.polkadotapp.feature_dotns_gateway_api.domain.model.DotNsLink
import io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.DotNsReservationMessage
import io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.api.accountAlias
import io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.api.dotNsGateway
import io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.api.dotNsGatewayCalls
import io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.api.registerName
import io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.config.DotNsGatewayConfig
import io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.config.DotNsGatewayConfigProvider
import io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.contract.PopContractCoder
import io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.queue.DotNsReservationQueueClearer
import io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.queue.DotNsReservationQueueReader
import io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.signer.origins.DotNsGatewayOrigins
import io.paritytech.polkadotapp.feature_revive_api.NameHash
import io.paritytech.polkadotapp.feature_revive_api.ReviveContractApi
import io.paritytech.polkadotapp.feature_revive_api.toEvmAccountId
import io.paritytech.polkadotapp.feature_transactions.api.data.ExtrinsicService
import io.paritytech.polkadotapp.feature_transactions.api.data.flattenExecutionFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RealDotNsGatewayRepository @Inject constructor(
    @param:LocalSourceQualifier private val localStorageSource: StorageDataSource,
    private val reviveContractApi: ReviveContractApi,
    private val queueReader: DotNsReservationQueueReader,
    private val queueClearer: DotNsReservationQueueClearer,
    private val configProvider: DotNsGatewayConfigProvider,
    private val tldProvider: DotNsTldProvider,
    private val extrinsicService: ExtrinsicService,
    private val dotNsGatewayOrigins: DotNsGatewayOrigins,
    private val chainRegistry: ChainRegistry,
    private val knownChains: KnownChains
) : DotNsGatewayRepository {
    override fun observeHasFullUsername(accountId: AccountId): Flow<Boolean> {
        return localStorageSource.subscribe(knownChains.assetHub) {
            metadata.dotNsGateway.accountAlias.observe(accountId)
                .map { alias -> alias != null }
        }
    }

    override suspend fun getBaseNameAvailability(
        baseLabel: String,
        accountId: AccountId
    ): Result<DotNsBaseNameAvailability> {
        return configProvider.getConfig().flatMap { config ->
            isRegistered(baseLabel, config).flatMap { registered ->
                if (registered) {
                    Result.success(DotNsBaseNameAvailability.TakenByOther)
                } else {
                    reservationAvailability(baseLabel, accountId, config)
                }
            }
        }
    }

    override fun reservationSigningPayload(
        candidate: AccountId,
        attester: AccountId,
        usernameBase: String,
        chatKey: EncodedPublicKey,
        reservedBaseLabel: String?,
        signedAt: Long
    ): ByteArray {
        return DotNsReservationMessage.signingPayload(
            candidate = candidate,
            attester = attester,
            usernameBase = usernameBase,
            chatKey = chatKey,
            reservedBaseLabel = reservedBaseLabel,
            signedAt = signedAt
        )
    }

    override suspend fun registerName(who: AccountId, label: String, link: DotNsLink): Result<Unit> {
        val chain = chainRegistry.getChain(knownChains.assetHub)

        return configProvider.getConfig()
            .flatMap { config -> queueClearer.prepareClaim(chain, who, label, config) }
            .flatMap {
                extrinsicService.submitExtrinsicAndAwaitExecution(
                    chain = chain,
                    origin = dotNsGatewayOrigins.asPersonRegistration(who, label, link)
                ) {
                    dotNsGatewayCalls.registerName(
                        who = who,
                        label = label,
                        link = link
                    )
                }
            }
            .flattenExecutionFailure()
            .coerceToUnit()
    }

    private suspend fun isRegistered(baseLabel: String, config: DotNsGatewayConfig): Result<Boolean> {
        return tldProvider.getTld().flatMap { tld ->
            val input = PopContractCoder.encodeChatKey(NameHash.nodeUnderTld(tld.value, baseLabel))

            reviveContractApi.callReadOnly(
                chainId = knownChains.assetHub,
                contract = config.popResolverAddress,
                input = input.toDataByteArray()
            ).map { output -> PopContractCoder.decodeChatKey(output.value) != null }
        }
    }

    private suspend fun reservationAvailability(
        baseLabel: String,
        accountId: AccountId,
        config: DotNsGatewayConfig
    ): Result<DotNsBaseNameAvailability> {
        return queueReader.read(baseLabel, config).map { queue ->
            val holder = queue.firstActiveHolder
            when {
                holder == null -> DotNsBaseNameAvailability.Free
                holder.contentEquals(accountId.toEvmAccountId().value) -> DotNsBaseNameAvailability.ReservedByUs
                else -> DotNsBaseNameAvailability.TakenByOther
            }
        }
    }
}
