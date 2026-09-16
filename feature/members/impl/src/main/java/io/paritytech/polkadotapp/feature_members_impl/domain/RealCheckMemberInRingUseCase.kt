package io.paritytech.polkadotapp.feature_members_impl.domain

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchPublicKey
import io.paritytech.polkadotapp.bandersnatch_crypto.memberKey
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.common.data.cache.CacheableDataConsistency
import io.paritytech.polkadotapp.common.utils.combineResults
import io.paritytech.polkadotapp.common.utils.firstIsInstance
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.flatMapNotNull
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.BandersnatchSecretsStorage
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.getMemberKey
import io.paritytech.polkadotapp.feature_members_api.data.model.RingCollectionId
import io.paritytech.polkadotapp.feature_members_api.data.model.RingPosition
import io.paritytech.polkadotapp.feature_members_api.data.model.includedOrNull
import io.paritytech.polkadotapp.feature_members_api.data.model.includesKey
import io.paritytech.polkadotapp.feature_members_api.data.repository.MembersRepository
import io.paritytech.polkadotapp.feature_members_api.data.repository.getMember
import io.paritytech.polkadotapp.feature_members_api.data.repository.getRingStatus
import io.paritytech.polkadotapp.feature_members_api.data.repository.subscribeMember
import io.paritytech.polkadotapp.feature_members_api.data.repository.subscribeRingStatus
import io.paritytech.polkadotapp.feature_members_api.domain.CheckMemberInRingUseCase
import io.paritytech.polkadotapp.feature_members_api.domain.model.MemberSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject

class RealCheckMemberInRingUseCase @Inject constructor(
    private val membersRepository: MembersRepository,
    private val bandersnatchSecretsStorage: BandersnatchSecretsStorage,
) : CheckMemberInRingUseCase {
    override suspend fun awaitIncluded(
        chainId: ChainId,
        collectionId: RingCollectionId,
        memberSource: MemberSource,
    ): Result<Unit> {
        return combineResults(resolveKey(memberSource), membersRepository.getRingKeysPageSize(chainId)) { key, keysPerPage ->
            key to keysPerPage
        }.mapCatching { (key, keysPerPage) ->
            Timber.i("Awaiting inclusion of $key in $collectionId on $chainId, keysPerPage=$keysPerPage")
            val included = awaitMemberIncluded(chainId, collectionId, key)
            Timber.i("Member is included at ${included.describe()}, awaiting the ring to cover it")
            awaitRingIncludesKey(chainId, collectionId, included, keysPerPage)
            Timber.i("Ring ${included.ringIndex.value} covers ${included.describe()}, inclusion complete")
        }.onFailure { Timber.e(it, "Awaiting inclusion failed") }
    }

    override suspend fun checkIncludes(
        chainId: ChainId,
        collectionId: RingCollectionId,
        memberSource: MemberSource,
    ): Result<Boolean> {
        return resolveKey(memberSource).flatMap { key ->
            membersRepository.getMember(chainId, collectionId, key, FRESH)
                .map { it?.includedOrNull() }
                .flatMapNotNull { included ->
                    combineResults(
                        membersRepository.getRingStatus(chainId, collectionId, included.ringIndex, FRESH),
                        membersRepository.getRingKeysPageSize(chainId),
                    ) { status, keysPerPage -> status != null && status.includesKey(included, keysPerPage) }
                }
                .map { it ?: false }
        }
    }

    private suspend fun awaitMemberIncluded(
        chainId: ChainId,
        collectionId: RingCollectionId,
        key: BandersnatchPublicKey,
    ): RingPosition.Included {
        return membersRepository.subscribeMember(chainId, collectionId, key, FRESH)
            .onEach { result ->
                result.fold(
                    onSuccess = { Timber.i("Member update: ${it?.describe() ?: "not a member"}") },
                    onFailure = { Timber.w(it, "Member update failed, skipping it") },
                )
            }
            .mapNotNull { it.getOrNull() }
            .firstIsInstance()
    }

    private suspend fun awaitRingIncludesKey(
        chainId: ChainId,
        collectionId: RingCollectionId,
        included: RingPosition.Included,
        keysPerPage: Int,
    ) {
        membersRepository.subscribeRingStatus(chainId, collectionId, included.ringIndex)
            .onEach { result ->
                result.fold(
                    onSuccess = { status ->
                        val description = status?.let {
                            "total=${it.total} included=${it.included} immutableSince=${it.immutableSince} " +
                                "coversKey=${it.includesKey(included, keysPerPage)}"
                        } ?: "no status"
                        Timber.i("Ring ${included.ringIndex.value} status update: $description")
                    },
                    onFailure = { Timber.w(it, "Ring ${included.ringIndex.value} status update failed, skipping it") },
                )
            }
            .mapNotNull { it.getOrNull() }
            .first { it.includesKey(included, keysPerPage) }
    }

    private fun RingPosition.describe(): String = when (this) {
        is RingPosition.Onboarding -> "onboarding (queuePage=$queuePage, queuedAt=$queuedAt)"
        is RingPosition.Included -> "ring=${ringIndex.value} page=$ringPage position=$ringPosition"
        RingPosition.Suspended -> "suspended"
    }

    private suspend fun resolveKey(memberSource: MemberSource): Result<BandersnatchPublicKey> = runCatching {
        when (memberSource) {
            is MemberSource.Account -> bandersnatchSecretsStorage.getMemberKey(memberSource.metaId)
            is MemberSource.Entropy -> memberSource.bandersnatchEntropy.memberKey()
        }
    }

    companion object {
        private val FRESH = CacheableDataConsistency.CONSISTENT_WITH_REMOTE
    }
}
