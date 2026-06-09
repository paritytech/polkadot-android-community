package io.paritytech.polkadotapp.feature_usernames_impl.domain.usecase

import io.novasama.substrate_sdk_android.scale.Schema
import io.novasama.substrate_sdk_android.scale.byteArray
import io.novasama.substrate_sdk_android.scale.sizedByteArray
import io.novasama.substrate_sdk_android.scale.toByteArray
import io.novasama.substrate_sdk_android.scale.uint8
import io.paritytech.polkadotapp.chains.util.invoke
import io.paritytech.polkadotapp.chains.util.signing.MessageSigningContext
import io.paritytech.polkadotapp.common.domain.model.EncodedPublicKey
import io.paritytech.polkadotapp.common.utils.Secp256r1KeyGenerator
import io.paritytech.polkadotapp.common.utils.removeHexPrefix
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.data.sign.AccountBytesSigner
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.BandersnatchSecretsStorage
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.getMemberKey
import io.paritytech.polkadotapp.feature_account_api.domain.model.SharedSecretDerivationDomain
import io.paritytech.polkadotapp.feature_account_api.domain.usecase.SharedSecretDerivationUseCase
import io.paritytech.polkadotapp.feature_usernames_api.domain.model.Username
import io.paritytech.polkadotapp.feature_usernames_api.domain.model.encoded
import io.paritytech.polkadotapp.feature_usernames_impl.domain.UsernamesChainProvider
import io.paritytech.polkadotapp.feature_usernames_impl.domain.model.ClaimUsernameParams
import javax.inject.Inject

interface CreateClaimParamsUseCase {
    suspend operator fun invoke(username: Username, attester: String, preferredDigits: String): Result<ClaimUsernameParams>
}

class RealCreateClaimParamsUseCase @Inject constructor(
    private val usernamesChainProvider: UsernamesChainProvider,
    private val bytesSigner: AccountBytesSigner,
    private val accountRepository: AccountRepository,
    private val sharedSecretDerivationUseCase: SharedSecretDerivationUseCase,
    private val keyGenerator: Secp256r1KeyGenerator,
    private val bandersnatchSecretsStorage: BandersnatchSecretsStorage
) : CreateClaimParamsUseCase {
    companion object {
        private const val MESSAGE_PREFIX = "pop:people-lite:register using"
    }

    override suspend fun invoke(username: Username, attester: String, preferredDigits: String): Result<ClaimUsernameParams> {
        return runCatching {
            val candidateSignature = getCandidateSignature().getOrThrow()
            val resourcesSignature = getResourcesSignature(username, attester).getOrThrow()
            val membershipSignature = getMembershipSignature().getOrThrow()

            ClaimUsernameParams(
                username = username.getDisplayUsername(),
                preferredDigits = preferredDigits,
                ringVrfKey = getMemberKeyBytes(),
                candidateAddress = getUserAddress(),
                candidateSignature = candidateSignature,
                consumerSignature = resourcesSignature,
                membershipSignature = membershipSignature,
                identifierKey = getChatPublicKey()
            )
        }
    }

    private suspend fun getPublicKey(): ByteArray {
        return accountRepository.getWalletAccount()
            .accountIdIn(usernamesChainProvider.chain()).value
    }

    private suspend fun getUserAddress(): String {
        return accountRepository.getWalletAccount()
            .addressIn(usernamesChainProvider.chain())
    }

    private suspend fun getMemberKeyBytes(): ByteArray {
        return bandersnatchSecretsStorage.getMemberKey(accountRepository.getWalletAccount().id)
            .value
    }

    private suspend fun getChatPublicKey(): EncodedPublicKey {
        val keypair = sharedSecretDerivationUseCase.deriveForDomain(SharedSecretDerivationDomain.CHAT)
        return keyGenerator.encode(keypair.public)
    }

    private suspend fun getMembershipSignature(): Result<ByteArray> {
        return bytesSigner.signWithBandersnatchByWallet(getLitePersonSignatureData(), MessageSigningContext.trustedContent())
    }

    private suspend fun getResourcesSignature(
        username: Username,
        attester: String,
    ): Result<ByteArray> {
        val publicKey = getPublicKey()
        val chatPublicKey = getChatPublicKey()

        val data = ResourcesSignatureSchema {
            it[ResourcesSignatureSchema.publicKey] = publicKey
            it[ResourcesSignatureSchema.verifier] = attester.removeHexPrefix().hexToByteArray()
            it[ResourcesSignatureSchema.chatPublicKey] = chatPublicKey.value
            it[ResourcesSignatureSchema.username] = username.encoded()
            it[ResourcesSignatureSchema.zero] = 0.toUByte()
        }
            .toByteArray()

        return bytesSigner.signRawBytesByWallet(data, usernamesChainProvider.chainId, MessageSigningContext.trustedContent())
            .map { it.signature }
    }

    private suspend fun getCandidateSignature(): Result<ByteArray> {
        val data = getLitePersonSignatureData()
        return bytesSigner.signRawBytesByWallet(data, usernamesChainProvider.chainId, MessageSigningContext.trustedContent())
            .map { it.signature }
    }

    private suspend fun getLitePersonSignatureData() =
        MESSAGE_PREFIX.encodeToByteArray() + getPublicKey() + getMemberKeyBytes()
}

private object ResourcesSignatureSchema : Schema<ResourcesSignatureSchema>() {
    val publicKey by sizedByteArray(32)
    val verifier by sizedByteArray(32)
    val chatPublicKey by sizedByteArray(65)
    val username by byteArray()
    val zero by uint8()
}
