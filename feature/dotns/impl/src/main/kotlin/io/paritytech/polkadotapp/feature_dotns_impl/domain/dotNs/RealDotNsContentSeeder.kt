package io.paritytech.polkadotapp.feature_dotns_impl.domain.dotNs

import io.novasama.substrate_sdk_android.extensions.toHexString
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsContentSeeder
import io.paritytech.polkadotapp.feature_dotns_impl.data.storage.ContentHash
import io.paritytech.polkadotapp.feature_dotns_impl.data.storage.ContentHashOverrides
import io.paritytech.polkadotapp.feature_dotns_impl.data.storage.DotNsContentStorage
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealDotNsContentSeeder @Inject constructor(
    private val contentStorage: DotNsContentStorage,
    private val contentHashOverrides: ContentHashOverrides,
) : DotNsContentSeeder {
    override suspend fun seedContent(dotNsName: String, files: Map<String, ByteArray>): String {
        val contentHash = generateContentHash(files)

        contentStorage.saveContent(contentHash, files)
        contentHashOverrides.putContentHashOverride(dotNsName, contentHash)

        return contentHash
    }

    private fun generateContentHash(files: Map<String, ByteArray>): ContentHash {
        val digest = MessageDigest.getInstance("SHA-256")
        files.entries.sortedBy { it.key }.forEach { (path, content) ->
            digest.update(path.toByteArray())
            digest.update(content)
        }
        return digest.digest().toHexString(withPrefix = false)
    }
}
