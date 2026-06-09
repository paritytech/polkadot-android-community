package io.paritytech.polkadotapp.feature_videogame_impl.data

import io.novasama.substrate_sdk_android.runtime.metadata.RuntimeMetadata
import io.novasama.substrate_sdk_android.runtime.metadata.module.Module
import io.paritytech.polkadotapp.chains.storage.source.query.api.QueryableModule
import io.paritytech.polkadotapp.chains.storage.source.query.api.QueryableStorageEntry0
import io.paritytech.polkadotapp.chains.storage.source.query.api.QueryableStorageEntry1
import io.paritytech.polkadotapp.chains.storage.source.query.api.QueryableStorageEntry2
import io.paritytech.polkadotapp.chains.storage.source.query.api.constant
import io.paritytech.polkadotapp.chains.storage.source.query.api.constantOrNull
import io.paritytech.polkadotapp.chains.storage.source.query.api.storage0
import io.paritytech.polkadotapp.chains.storage.source.query.api.storage0OrNull
import io.paritytech.polkadotapp.chains.storage.source.query.api.storage1
import io.paritytech.polkadotapp.chains.storage.source.query.api.storage2
import io.paritytech.polkadotapp.chains.util.WithRuntime
import io.paritytech.polkadotapp.chains.util.videoGame
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.EncodedPublicKey
import io.paritytech.polkadotapp.common.domain.model.Timestamp
import io.paritytech.polkadotapp.feature_people_api.domain.models.PersonalAlias
import io.paritytech.polkadotapp.feature_videogame_api.domain.state.model.GameIndex
import io.paritytech.polkadotapp.feature_videogame_impl.data.models.onchain.OnChainAccountOrPerson
import io.paritytech.polkadotapp.feature_videogame_impl.data.models.onchain.OnChainArchivedPlayer
import io.paritytech.polkadotapp.feature_videogame_impl.data.models.onchain.OnChainVideoGameInfo
import io.paritytech.polkadotapp.feature_videogame_impl.data.models.onchain.OnChainVideoGamePhaseDurations
import io.paritytech.polkadotapp.feature_videogame_impl.data.models.onchain.OnChainVideoGamePlayerInfo
import io.paritytech.polkadotapp.feature_videogame_impl.data.models.onchain.OnChainVideoGamePlayerRoundKey
import io.paritytech.polkadotapp.feature_videogame_impl.data.models.onchain.OnChainVideoGameSchedule

@JvmInline
value class VideoGameApi(override val module: Module) : QueryableModule

context(WithRuntime)
val RuntimeMetadata.videoGame: VideoGameApi
    get() = VideoGameApi(videoGame())

context(WithRuntime)
val VideoGameApi.game: QueryableStorageEntry0<OnChainVideoGameInfo>
    get() = storage0("Game")

context(WithRuntime)
val VideoGameApi.gameIndex: QueryableStorageEntry0<Int>
    get() = storage0("GameIndex")

context(WithRuntime)
val VideoGameApi.indexToPlayer: QueryableStorageEntry1<OnChainVideoGamePlayerRoundKey, OnChainAccountOrPerson>
    get() = storage1("IndexToPlayer")

context(WithRuntime)
val VideoGameApi.playerToIndex: QueryableStorageEntry1<OnChainAccountOrPerson, List<Int>>
    get() = storage1("PlayerToIndex")

context(WithRuntime)
val VideoGameApi.players: QueryableStorageEntry1<OnChainAccountOrPerson, OnChainVideoGamePlayerInfo>
    get() = storage1("Players")

// Double map keyed (owner, nftHash) → mintedAt (u32 unix seconds). The nftHash is the
// deterministic attestation id from AttestationHashCalculator; the owner is the attestee.
context(WithRuntime)
val VideoGameApi.nfts: QueryableStorageEntry2<OnChainAccountOrPerson, AttestationNftHash, Int>
    get() = storage2("Nfts")

context(WithRuntime)
val VideoGameApi.archivedPlayers: QueryableStorageEntry1<OnChainAccountOrPerson, OnChainArchivedPlayer>
    get() = storage1("ArchivedPlayers")

context(WithRuntime)
val VideoGameApi.pendingInvites: QueryableStorageEntry2<AccountId, EncodedPublicKey, Unit>
    get() = storage2("PendingInvites")

context(WithRuntime)
val VideoGameApi.nftCandidates: QueryableStorageEntry2<OnChainAccountOrPerson, ByteArray, Unit>
    get() = storage2("NftCandidates")

context(WithRuntime)
val VideoGameApi.schedule: QueryableStorageEntry0<List<OnChainVideoGameSchedule>>
    get() = storage0("GameSchedules")

context(WithRuntime)
val VideoGameApi.storedPhaseDurationsOrNull: QueryableStorageEntry0<OnChainVideoGamePhaseDurations>?
    get() = storage0OrNull("StoredPhaseDurations", "TestnetStoredPhaseDurations")

context(WithRuntime)
val VideoGameApi.aliasToStmtAccount: QueryableStorageEntry1<PersonalAlias, AccountId>
    get() = storage1("AliasToStmtAccount")

context(WithRuntime)
val VideoGameApi.playerAttendanceHistory: QueryableStorageEntry1<OnChainAccountOrPerson, List<GameIndex>>
    get() = storage1("PlayerAttendanceHistory")

context(WithRuntime)
val VideoGameApi.gameHistory: QueryableStorageEntry1<GameIndex, Timestamp>
    get() = storage1("GameHistory")

context(WithRuntime)
val VideoGameApi.phaseDurations: OnChainVideoGamePhaseDurations
    get() = constantOrNull("DefaultPhaseDurations") ?: constant("PhaseDurations")

context(WithRuntime)
val VideoGameApi.communicationIdentifiers: QueryableStorageEntry1<AccountId, EncodedPublicKey>
    get() = storage1("CommunicationIdentifiers")
