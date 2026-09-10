package io.paritytech.polkadotapp.feature_coinage_impl.domain.installation

import io.novasama.substrate_sdk_android.extensions.toHexString
import io.paritytech.polkadotapp.common.domain.model.hexToDataByteArray
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageInstallationId
import io.paritytech.polkadotapp.feature_revive_api.EvmAccountId
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.OperationGroupId
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.TxDomainId

const val COINAGE_INSTALLATION_DOMAIN_ID = "coinage-installation"

val COINAGE_INSTALLATION_DOMAIN = TxDomainId(COINAGE_INSTALLATION_DOMAIN_ID)

data class InstallationRegistrationTarget(
    val contract: EvmAccountId,
    val installation: CoinageInstallationId,
)

// The group is the whole history of registering one installation in one contract. Naming both keeps the oracle
// reading the contract an attempt actually wrote to, and makes a contract change start a registration of its own.
fun InstallationRegistrationTarget.registrationGroup(): OperationGroupId {
    val contractHex = contract.value.toHexString(withPrefix = true)
    val installationHex = installation.value.value.toHexString(withPrefix = true)

    return OperationGroupId("$contractHex$GROUP_SEPARATOR$installationHex")
}

fun OperationGroupId.registrationTargetOrNull(): InstallationRegistrationTarget? {
    val parts = value.split(GROUP_SEPARATOR)
    if (parts.size != 2) return null

    return runCatching {
        InstallationRegistrationTarget(
            contract = parts[0].hexToDataByteArray(),
            installation = CoinageInstallationId(parts[1].hexToDataByteArray()),
        )
    }.getOrNull()
}

private const val GROUP_SEPARATOR = "/"
