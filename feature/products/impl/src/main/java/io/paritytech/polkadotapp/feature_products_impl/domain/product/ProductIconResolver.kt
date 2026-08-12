package io.paritytech.polkadotapp.feature_products_impl.domain.product

import com.google.gson.Gson
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsResolver
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.tools_ipfs_api.IpfsContentLookup
import timber.log.Timber
import javax.inject.Inject

private const val MANIFEST_METADATA_KEY = "manifest"

/**
 * Resolves a product's display icon from its on-chain root manifest: reads the `manifest` text record,
 * takes the icon CID, and turns it into an IPFS gateway URL. Returns null when the product publishes no
 * manifest/icon or resolution fails.
 */
interface ProductIconResolver {
    suspend fun resolveIconUrl(productId: ProductId): String?
}

class RealProductIconResolver @Inject constructor(
    private val dotNsResolver: DotNsResolver,
    private val ipfsContentLookup: IpfsContentLookup,
    private val gson: Gson,
) : ProductIconResolver {
    override suspend fun resolveIconUrl(productId: ProductId): String? {
        val manifestJson = dotNsResolver.getMetadataEntry(productId.value, MANIFEST_METADATA_KEY)
            .onFailure { Timber.e(it, "Failed to read manifest for product ${productId.value}") }
            .getOrNull() ?: return null
        val cid = runCatching { gson.fromJson(manifestJson, ProductManifestDto::class.java) }
            .onFailure { Timber.e(it, "Failed to parse product manifest for ${productId.value}") }
            .getOrNull()?.icon?.cid ?: return null
        return ipfsContentLookup.getIpfsLinkFor(cid)
            .onFailure { Timber.e(it, "Failed to resolve IPFS link for icon cid $cid") }
            .getOrNull()
    }

    private data class ProductManifestDto(val icon: ProductManifestIconDto?)

    private data class ProductManifestIconDto(val cid: String?)
}
