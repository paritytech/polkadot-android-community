package io.paritytech.polkadotapp.feature_products_impl.data.storage

import io.novasama.substrate_sdk_android.extensions.fromHex
import io.novasama.substrate_sdk_android.extensions.toHexString
import io.paritytech.polkadotapp.common.data.storage.preferences.Preferences
import io.paritytech.polkadotapp.common.data.storage.preferences.encrypted.EncryptedPreferences
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_account_api.domain.derivation.DerivationIndex32
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageOperationGroupId
import io.paritytech.polkadotapp.feature_products_impl.domain.topUpRequest.PaymentTopUpSource
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

private const val SOURCE_PREFIX = "TopUpSource."

/** Which sources are still held. Plain rather than encrypted: the encrypted store cannot be enumerated. */
private const val HELD_INDEX = "TopUpSource.held"

/** A source a top-up can still be attempted from. */
data class HeldTopUpSource(val groupId: CoinageOperationGroupId, val source: PaymentTopUpSource)

/**
 * The keys a top-up still in flight would need to try again.
 *
 * Deliberately not a record of the top-up — that is the coinage group id, which the ledger already holds.
 * This exists for the two things the ledger structurally cannot do: the entries carry a coin's *public* key
 * and no extrinsic bytes, so a registered transaction can be decided after a relaunch but never rebuilt; and
 * a top-up whose funds have not landed yet has no entries at all, so nothing else knows it exists.
 *
 * Held only while another attempt could still be made, and dropped the moment one could not.
 */
interface TopUpSourceStorage {
    suspend fun held(): List<HeldTopUpSource>

    suspend fun put(groupId: CoinageOperationGroupId, source: PaymentTopUpSource): Result<Unit>

    suspend fun remove(groupId: CoinageOperationGroupId)
}

class RealTopUpSourceStorage @Inject constructor(
    private val encryptedPreferences: EncryptedPreferences,
    private val preferences: Preferences,
    private val dispatchers: CoroutineDispatchers,
) : TopUpSourceStorage {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun held(): List<HeldTopUpSource> = withContext(dispatchers.io) {
        preferences.getStringSet(HELD_INDEX).mapNotNull { groupId ->
            read(CoinageOperationGroupId(groupId))
        }
    }

    override suspend fun put(
        groupId: CoinageOperationGroupId,
        source: PaymentTopUpSource,
    ): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            encryptedPreferences.putEncryptedString(prefsKey(groupId), json.encodeToString(source.toStored()))

            // Indexed after the source, so an index entry always names something readable. The other order
            // would have a launch trying to attempt a top-up whose keys had never been written.
            preferences.putStringSet(HELD_INDEX, preferences.getStringSet(HELD_INDEX) + groupId.value)
        }.logFailure("Failed to hold the source for ${groupId.value}")
    }

    override suspend fun remove(groupId: CoinageOperationGroupId) = withContext(dispatchers.io) {
        preferences.putStringSet(HELD_INDEX, preferences.getStringSet(HELD_INDEX) - groupId.value)
        encryptedPreferences.removeKey(prefsKey(groupId))
    }

    private fun read(groupId: CoinageOperationGroupId): HeldTopUpSource? {
        val stored = encryptedPreferences.getDecryptedString(prefsKey(groupId)) ?: return null

        return runCatching { HeldTopUpSource(groupId, json.decodeFromString<StoredSource>(stored).toDomain()) }
            .logFailure("Failed to read the source held for ${groupId.value}")
            .getOrNull()
    }

    private fun prefsKey(groupId: CoinageOperationGroupId) = SOURCE_PREFIX + groupId.value
}

@Serializable
private class StoredSource(
    val tag: SourceTag,
    val hex: String? = null,
    val listHex: List<String>? = null,
)

@Serializable
private enum class SourceTag {
    @SerialName("ProductAccount")
    PRODUCT_ACCOUNT,

    @SerialName("PrivateKey")
    PRIVATE_KEY,

    @SerialName("Coins")
    COINS,
}

private fun PaymentTopUpSource.toStored() = when (this) {
    is PaymentTopUpSource.ProductAccount ->
        StoredSource(SourceTag.PRODUCT_ACCOUNT, hex = index.bytes.value.toHexString())

    is PaymentTopUpSource.PrivateKey -> StoredSource(SourceTag.PRIVATE_KEY, hex = key.value.toHexString())

    is PaymentTopUpSource.Coins ->
        StoredSource(SourceTag.COINS, listHex = secretKeys.map { it.value.toHexString() })
}

private fun StoredSource.toDomain(): PaymentTopUpSource = when (tag) {
    SourceTag.PRODUCT_ACCOUNT -> {
        val index = DerivationIndex32.fromBytes(requiredHex(hex)).getOrNull()

        PaymentTopUpSource.ProductAccount(requireNotNull(index) { "top-up held an unusable derivation index" })
    }

    SourceTag.PRIVATE_KEY -> PaymentTopUpSource.PrivateKey(requiredHex(hex))

    SourceTag.COINS -> PaymentTopUpSource.Coins(
        requireNotNull(listHex) { "Coins top-up held without its keys" }.map { DataByteArray(it.fromHex()) }
    )
}

private fun requiredHex(hex: String?) =
    DataByteArray(requireNotNull(hex) { "top-up held without its source" }.fromHex())
