package io.paritytech.polkadotapp.feature_dotns_api.domain

import java.io.IOException

sealed class DotNsResolutionError(message: String, cause: Throwable?) : Exception(message, cause) {
    class NotFound(dotNsName: String) :
        DotNsResolutionError("Domain $dotNsName is not registered or has no content", null)

    class RegistryUnreachable(cause: Throwable) : DotNsResolutionError("dotNS registry read failed", cause)

    class InvalidContentHash(cause: Throwable) : DotNsResolutionError("Content hash is not a supported IPFS CID", cause)

    class GatewayUnreachable(cause: IOException) : DotNsResolutionError("IPFS gateway unreachable", cause)

    class GatewayRejected(val statusCode: Int) : DotNsResolutionError("IPFS gateway answered HTTP $statusCode", null)

    class ContentDamaged(cause: Throwable) : DotNsResolutionError("dotNS content could not be unpacked or stored", cause)
}
