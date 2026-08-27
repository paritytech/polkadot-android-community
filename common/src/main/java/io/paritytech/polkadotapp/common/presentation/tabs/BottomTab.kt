package io.paritytech.polkadotapp.common.presentation.tabs

import io.paritytech.polkadotapp.common.BuildConfig
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

enum class BottomTab {
    CHATS,
    WALLET,
    EXPLORE,
    SETTINGS;

    companion object {
        val availableEntries: ImmutableList<BottomTab> = entries
            .filter { BuildConfig.BROWSE_TAB_ENABLED || it != EXPLORE }
            .toImmutableList()
    }
}
