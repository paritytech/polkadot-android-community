package io.paritytech.polkadotapp.common.presentation.tabs

import io.paritytech.polkadotapp.common.BuildConfig

enum class BottomTab {
    CHATS,
    WALLET,
    EXPLORE,
    SETTINGS;

    companion object {
        /**
         * Tabs surfaced in the bottom navigation for the current build.
         * Chat is hidden in enterprise POS builds (CHAT_ENABLED = false for nightly/release).
         */
        val visibleEntries: List<BottomTab>
            get() = entries.filter { it != CHATS || BuildConfig.CHAT_ENABLED }
    }
}
