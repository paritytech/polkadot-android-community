package io.paritytech.polkadotapp.app.root.navigation

import android.annotation.SuppressLint
import androidx.navigation.NavHostController
import androidx.navigation.fragment.DialogFragmentNavigator
import androidx.navigation.fragment.NavHostFragment
import io.paritytech.polkadotapp.app.R

class NovaNavHostFragment : NavHostFragment() {
    @SuppressLint("MissingSuperCall")
    override fun onCreateNavHostController(navHostController: NavHostController) {
        navHostController.navigatorProvider.addNavigator(DialogFragmentNavigator(requireContext(), childFragmentManager))

        val addFragmentNavigator = AddFragmentNavigator(requireContext(), childFragmentManager, R.id.rootNavHost)

        navHostController.navigatorProvider.addNavigator(addFragmentNavigator)
    }
}
