package io.paritytech.polkadotapp.feature_chats_impl.presentation.error

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.paritytech.polkadotapp.common.presentation.ui.errors.PresentationError
import io.paritytech.polkadotapp.common.presentation.ui.errors.StringResPresentationError
import io.paritytech.polkadotapp.common.presentation.ui.errors.UnexpectedPresentationError
import io.paritytech.polkadotapp.common.utils.InformationSize
import io.paritytech.polkadotapp.feature_chats_impl.domain.error.AttachmentError
import io.paritytech.polkadotapp.feature_chats_impl.domain.error.ChatRequestError
import io.paritytech.polkadotapp.common.R as RCommon

fun AttachmentError.toPresentationError(): PresentationError = when (this) {
    is AttachmentError.FileTooLarge -> FileTooLargePresentationError(maxSize)

    is AttachmentError.Unknown -> UnexpectedPresentationError()
}

fun ChatRequestError.toPresentationError(): PresentationError = when (this) {
    ChatRequestError.ContactNotFound -> StringResPresentationError(RCommon.string.chat_error_contact_not_found)

    is ChatRequestError.Unknown -> UnexpectedPresentationError()
}

// Carries the limit the size check actually enforced, so the message can never drift from it.
private class FileTooLargePresentationError(private val maxSize: InformationSize) : PresentationError {
    @Composable
    override fun message(): String {
        return stringResource(RCommon.string.chat_error_attachment_too_large, maxSize.inWholeMegabytes)
    }
}
