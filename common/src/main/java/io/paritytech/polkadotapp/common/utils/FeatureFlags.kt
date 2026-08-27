package io.paritytech.polkadotapp.common.utils

import io.paritytech.polkadotapp.common.BuildConfig

object FeatureFlags {
    fun isEnabled(feature: FeatureOption): Boolean {
        return when (feature) {
            FeatureOption.ALLOW_SHORT_EVIDENCE_VIDEO -> BuildConfig.ALLOW_SHORT_EVIDENCE_VIDEO
            FeatureOption.SHOW_MOB_RULE_CASE_FOR_DEVELOPMENT -> BuildConfig.DEBUG
            FeatureOption.SHORT_WORKER_BACKOFF -> BuildConfig.DEBUG
            FeatureOption.LOW_BATTERY_EVIDENCE_PROVISION -> BuildConfig.DEBUG
            FeatureOption.SKIP_MOBRULE_CASE -> BuildConfig.DEBUG
            FeatureOption.SAMPLE_BOT -> BuildConfig.SAMPLE_BOT
            FeatureOption.DIM1_BOT_BY_DEFAULT -> BuildConfig.DIM1_BOT_BY_DEFAULT
            FeatureOption.DIM2_BOT_BY_DEFAULT -> BuildConfig.DIM2_BOT_BY_DEFAULT
            FeatureOption.PEER_BOT_BY_DEFAULT -> BuildConfig.PEER_BOT_BY_DEFAULT
            FeatureOption.BROWSE_TAB -> BuildConfig.BROWSE_TAB_ENABLED
            FeatureOption.CHAT_EXTENSIONS -> BuildConfig.CHAT_EXTENSIONS_ENABLED
        }
    }
}

enum class FeatureOption {
    SHOW_MOB_RULE_CASE_FOR_DEVELOPMENT,
    ALLOW_SHORT_EVIDENCE_VIDEO,
    SHORT_WORKER_BACKOFF,
    LOW_BATTERY_EVIDENCE_PROVISION,
    SKIP_MOBRULE_CASE,
    SAMPLE_BOT,
    DIM1_BOT_BY_DEFAULT,
    DIM2_BOT_BY_DEFAULT,
    PEER_BOT_BY_DEFAULT,
    BROWSE_TAB,
    CHAT_EXTENSIONS
}

val FeatureOption.isEnabled
    get() = FeatureFlags.isEnabled(this)

val FeatureOption.isDisabled
    get() = FeatureFlags.isEnabled(this).not()
