package io.paritytech.polkadotapp.feature_account_api.presentation.address.model

class ExtractedAddressesSection(
    val category: ExtractedAddressesCategory,
    val addresses: List<ExtractedAddress>
) {
    companion object {
        fun general(addresses: List<ExtractedAddress>): ExtractedAddressesSection {
            return ExtractedAddressesSection(
                category = ExtractedAddressesCategory.General,
                addresses = addresses
            )
        }
    }
}

inline fun ExtractedAddressesSection.mapAddresses(transform: (ExtractedAddress) -> ExtractedAddress?): ExtractedAddressesSection =
    ExtractedAddressesSection(category = category, addresses = addresses.mapNotNull(transform))

sealed class ExtractedAddressesCategory {
    data object General : ExtractedAddressesCategory()

    class Custom(val label: String) : ExtractedAddressesCategory()
}
