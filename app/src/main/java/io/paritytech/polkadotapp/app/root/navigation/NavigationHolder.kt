package io.paritytech.polkadotapp.app.root.navigation

import androidx.navigation.NavController
import io.paritytech.polkadotapp.common.presentation.resources.ContextManager
import io.paritytech.polkadotapp.common.presentation.tabs.BottomTab
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject

class NavigationHolder @Inject constructor(val contextManager: ContextManager) {
    var navController: NavController? = null
        private set

    val tabRequests: SharedFlow<BottomTab>
        field = MutableSharedFlow(extraBufferCapacity = 1)

    fun attach(navController: NavController) {
        this.navController = navController
    }

    fun detach() {
        navController = null
    }

    fun requestTab(tab: BottomTab) {
        tabRequests.tryEmit(tab)
    }

    fun finishApp() {
        contextManager.getActivity()?.finish()
    }
}

fun NavigationHolder.executeBack() {
    val popped = navController!!.popBackStack()

    if (!popped) {
        contextManager.getActivity()!!.finish()
    }
}
