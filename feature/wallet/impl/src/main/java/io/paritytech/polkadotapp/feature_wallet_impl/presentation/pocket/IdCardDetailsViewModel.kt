package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket

import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.feature_wallet_impl.PocketRouter
import javax.inject.Inject

@HiltViewModel
class IdCardDetailsViewModel @Inject constructor(
    private val router: PocketRouter
) : BaseViewModel() {
    fun openScanner() {
        router.openScan()
    }
}
