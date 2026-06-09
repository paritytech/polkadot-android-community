package io.paritytech.polkadotapp.common.presentation.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import io.paritytech.polkadotapp.common.utils.observe

open class BaseScreenDelegate(
    private val context: () -> Context,
    private val viewModel: () -> BaseViewModel
) {
    context(LifecycleOwner)
    fun subscribeViewModelEvents() {
        viewModel().events.observe(::handleEvent)
    }

    private fun handleEvent(event: BaseViewModelEvent) {
        when (event) {
            is BaseViewModelEvent.Error -> showMessage("Error: ${event.errorTitle}")
            is BaseViewModelEvent.ErrorWithTitle -> showMessage("TODO implement how to show errors: ${event.title} ${event.message}")
            is BaseViewModelEvent.Message -> showMessage(event.message)
        }
    }

    fun showMessage(text: String) {
        Toast.makeText(context(), text, Toast.LENGTH_SHORT)
            .show()
    }
}

class BaseFragmentDelegate(
    private val fragment: Fragment,
    viewModel: () -> BaseViewModel
) : BaseScreenDelegate(
    context = { fragment.requireContext() },
    viewModel = viewModel
) {
    fun overwriteOnBackPressed(action: () -> Unit) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                action()
            }
        }

        fragment.requireActivity().onBackPressedDispatcher.addCallback(
            owner = fragment.viewLifecycleOwner,
            onBackPressedCallback = callback
        )
    }
}

context(Fragment)
fun BaseFragmentDelegate(
    viewModel: () -> BaseViewModel
): BaseFragmentDelegate = BaseFragmentDelegate(fragment = this@Fragment, viewModel = viewModel)
