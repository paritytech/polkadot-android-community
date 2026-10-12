package io.paritytech.polkadotapp.feature_coinage_impl.presentation.mixin

import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.feature_coinage_api.presentation.mixin.CoinageSetupNoticeMixin
import io.paritytech.polkadotapp.feature_coinage_impl.domain.installation.CoinageInstallationGate
import javax.inject.Inject

class RealCoinageSetupNoticeMixinFactory @Inject constructor(
    private val gate: CoinageInstallationGate,
) : CoinageSetupNoticeMixin.Factory {
    override fun create(scope: ComputationalScope): CoinageSetupNoticeMixin = RealCoinageSetupNoticeMixin(scope, gate)
}
