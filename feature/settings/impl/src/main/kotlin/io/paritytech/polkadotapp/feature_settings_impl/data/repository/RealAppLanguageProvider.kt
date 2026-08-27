package io.paritytech.polkadotapp.feature_settings_impl.data.repository

import io.paritytech.polkadotapp.feature_settings_api.AppLanguageProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RealAppLanguageProvider @Inject constructor(
    private val languageRepository: LanguageRepository
) : AppLanguageProvider {
    override val selectedLanguageCode: Flow<String> =
        languageRepository.selectedLanguage.map { it.code }
}
