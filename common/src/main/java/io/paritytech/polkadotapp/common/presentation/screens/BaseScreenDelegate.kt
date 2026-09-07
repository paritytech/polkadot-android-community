package io.paritytech.polkadotapp.common.presentation.screens

import android.content.Context
import android.widget.Toast

class BaseScreenDelegate(
    private val context: () -> Context,
) {
    fun showMessage(text: String) {
        Toast.makeText(context(), text, Toast.LENGTH_SHORT)
            .show()
    }
}
