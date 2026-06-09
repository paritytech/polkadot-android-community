package io.paritytech.polkadotapp.common.utils

import android.content.DialogInterface
import android.view.View

context(DialogInterface)
fun View.setDismissingClickListener(listener: (View) -> Unit) {
    setOnClickListener {
        listener.invoke(it)

        this@DialogInterface.dismiss()
    }
}
