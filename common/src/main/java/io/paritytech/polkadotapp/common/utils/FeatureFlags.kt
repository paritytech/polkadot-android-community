package io.paritytech.polkadotapp.common.utils

import io.paritytech.polkadotapp.common.BuildConfig

object FeatureFlags {
    private val fullFeatured = !BuildConfig.SAFETY_MODE

    fun isEnabled(feature: FeatureOption): Boolean {
        return when (feature) {
            FeatureOption.SHOW_MOB_RULE_CASE_FOR_DEVELOPMENT,
            FeatureOption.SHORT_WORKER_BACKOFF,
            FeatureOption.LOW_BATTERY_EVIDENCE_PROVISION,
            FeatureOption.SKIP_MOBRULE_CASE,
            FeatureOption.DEBUG_MENU -> BuildConfig.DEBUG

            FeatureOption.ARBITRARY_PRODUCTS,
            FeatureOption.BROWSE_TAB,
            FeatureOption.ALL_CHAT_EXTENSIONS,
            FeatureOption.LINKED_DEVICES,
            FeatureOption.PRODUCT_SETTINGS,
            FeatureOption.PERSONHOOD,
            FeatureOption.COLLECTIBLES -> fullFeatured

            FeatureOption.ALLOW_SHORT_EVIDENCE_VIDEO -> BuildConfig.ALLOW_SHORT_EVIDENCE_VIDEO
            FeatureOption.SAMPLE_BOT -> BuildConfig.SAMPLE_BOT
            FeatureOption.DIM1_BOT_BY_DEFAULT -> BuildConfig.DIM1_BOT_BY_DEFAULT
            FeatureOption.DIM2_BOT_BY_DEFAULT -> BuildConfig.DIM2_BOT_BY_DEFAULT
            FeatureOption.PEER_BOT_BY_DEFAULT -> BuildConfig.PEER_BOT_BY_DEFAULT
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
    DEBUG_MENU,
    BROWSE_TAB,
    ALL_CHAT_EXTENSIONS,
    LINKED_DEVICES,
    PRODUCT_SETTINGS,
    PERSONHOOD,
    COLLECTIBLES,
    ARBITRARY_PRODUCTS
}

val FeatureOption.isEnabled
    get() = FeatureFlags.isEnabled(this)

val FeatureOption.isDisabled
    get() = FeatureFlags.isEnabled(this).not()
