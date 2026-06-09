package io.paritytech.polkadotapp.feature_settings_impl.presentation.backup.mnemonic

import io.paritytech.polkadotapp.design.components.mnemonic.model.Word
import kotlinx.coroutines.flow.StateFlow

interface MnemonicRevealContract {
    val mnemonic: StateFlow<List<Word>>
    val isMnemonicHidden: StateFlow<Boolean>

    fun back()
    fun revealMnemonic()
}
