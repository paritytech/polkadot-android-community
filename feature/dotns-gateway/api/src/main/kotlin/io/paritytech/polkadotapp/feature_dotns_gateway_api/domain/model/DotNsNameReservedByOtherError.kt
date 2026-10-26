package io.paritytech.polkadotapp.feature_dotns_gateway_api.domain.model

/** Another account holds the first active reservation of [label], so only that account can claim it. */
class DotNsNameReservedByOtherError(val label: String) : Exception("\"$label\" is reserved for another account")
