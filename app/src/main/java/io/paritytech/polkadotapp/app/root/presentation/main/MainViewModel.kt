package io.paritytech.polkadotapp.app.root.presentation.main

import android.Manifest
import android.adservices.appsetid.AppSetIdManager
import android.os.Build
import android.os.ext.SdkExtensions
import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.app.root.domain.ObserveTabWarningsUseCase
import io.paritytech.polkadotapp.app.root.navigation.NavigationHolder
import io.paritytech.polkadotapp.common.presentation.resources.ContextManager
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.common.presentation.tabs.BottomTab
import io.paritytech.polkadotapp.common.utils.permissions.PermissionAsker
import io.paritytech.polkadotapp.common.utils.stateInBackground
import io.paritytech.polkadotapp.feature_chats_api.domain.ChatMessageSender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    contextManager: ContextManager,
    observeTabWarningsUseCase: ObserveTabWarningsUseCase,
    navigationHolder: NavigationHolder,
    private val permissionAsker: PermissionAsker,
    private val messageSender: ChatMessageSender
) : BaseViewModel() {
    val tabWarnings: StateFlow<Map<BottomTab, Boolean>> = observeTabWarningsUseCase()
        .stateInBackground(
            started = SharingStarted.Eagerly,
            initialValue = emptyMap()
        )

    val currentTab: StateFlow<BottomTab>
        field = MutableStateFlow(BottomTab.CHATS)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            SdkExtensions.getExtensionVersion(SdkExtensions.AD_SERVICES) >= 6
        ) {
            AppSetIdManager.get(contextManager.requireActivity())
        }

        messageSender.startExtensions()

        launch {
            permissionAsker.askPermission(Manifest.permission.POST_NOTIFICATIONS)
        }

        navigationHolder
            .tabRequests
            .onEach(::selectTab)
            .launchIn(this)
    }

    fun selectTab(tab: BottomTab) {
        currentTab.value = tab
    }
}
