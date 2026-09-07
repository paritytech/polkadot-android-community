@file:Suppress("FunctionName")

package io.paritytech.polkadotapp.common.presentation.ui.errors

import io.paritytech.polkadotapp.common.R

fun NoConnectionPresentationError(): PresentationError =
    StringResPresentationError(R.string.common_error_no_connection)

fun ServerPresentationError(): PresentationError =
    StringResPresentationError(R.string.common_error_server)

fun SigningFailedPresentationError(): PresentationError =
    StringResPresentationError(R.string.common_error_signing_failed)

fun UnexpectedPresentationError(): PresentationError =
    StringResPresentationError(R.string.generic_error_notification)
