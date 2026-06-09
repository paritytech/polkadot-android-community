package io.paritytech.polkadotapp.common.presentation.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.fragment.findNavController
import io.paritytech.polkadotapp.design.theme.PolkadotTheme

abstract class BaseComposeFragment<T : BaseViewModel> : Fragment() {
    protected abstract val viewModel: T

    private val delegate = BaseFragmentDelegate(::viewModel)

    @Composable
    protected abstract fun Screen()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

        setContent {
            PolkadotTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true }
                ) {
                    Screen()
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        delegate.subscribeViewModelEvents()
    }

    /**
     * Jetpack navigation specific observeResult functionality.
     */
    protected fun <T : Any> observeResult(
        key: String,
        lifecycleOwner: LifecycleOwner = viewLifecycleOwner,
        onResult: (T) -> Unit,
    ) {
        val navController = findNavController()
        navController.currentBackStackEntry?.savedStateHandle?.getLiveData<T>(key)?.observe(lifecycleOwner) { result ->
            onResult(result)
            navController.currentBackStackEntry?.savedStateHandle?.remove<T>(key)
        }
    }
}
