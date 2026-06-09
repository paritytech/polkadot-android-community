package io.paritytech.polkadotapp.feature_backup_impl.mnemonic.confirm.models

import androidx.compose.runtime.Immutable
import io.paritytech.polkadotapp.design.components.mnemonic.model.Word

@Immutable
data class ConfirmationState(
    val addedWords: List<Word> = emptyList(),
    val allWords: List<Word> = emptyList()
)
