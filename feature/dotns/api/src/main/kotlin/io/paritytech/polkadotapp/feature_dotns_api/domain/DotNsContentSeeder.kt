package io.paritytech.polkadotapp.feature_dotns_api.domain

/**
 * Pre-seeds content for a .dot domain so that [DotNsResolver] can serve it locally
 * without resolving from chain. Used by the debug menu to inject content from HTTP sources.
 *
 * @return the generated content hash for the seeded content
 */
interface DotNsContentSeeder {
    suspend fun seedContent(dotNsName: String, files: Map<String, ByteArray>): String
}
