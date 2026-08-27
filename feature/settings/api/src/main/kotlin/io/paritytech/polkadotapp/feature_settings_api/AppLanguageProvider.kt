package io.paritytech.polkadotapp.feature_settings_api

import kotlinx.coroutines.flow.Flow

/**
 * Read access to the language the user selected in Settings, for modules that
 * must follow it without depending on the settings implementation.
 */
interface AppLanguageProvider {
    /** BCP 47 tag of the selected language, re-emitted on every change. */
    val selectedLanguageCode: Flow<String>
}
