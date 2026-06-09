package io.paritytech.polkadotapp.common.presentation.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.common.domain.errors.SigningCancelledException
import io.paritytech.polkadotapp.common.utils.OneShotEventChannel
import kotlinx.coroutines.flow.receiveAsFlow
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

open class BaseViewModel : ViewModel(), ComputationalScope {
    private val _events = OneShotEventChannel<BaseViewModelEvent>()
    val events = _events.receiveAsFlow()

    protected fun showMessage(text: String) {
        _events.trySend(BaseViewModelEvent.Message(text))
    }

    protected fun showError(
        title: String,
        text: String,
    ) {
        _events.trySend(BaseViewModelEvent.ErrorWithTitle(title, text))
    }

    protected fun showError(text: String) {
        _events.trySend(BaseViewModelEvent.Error(text))
    }

    protected fun showError(throwable: Throwable) {
        if (!shouldIgnore(throwable)) {
            Timber.e(throwable)

            showError(throwable.message ?: "Unknown failure")
        }
    }

    override val coroutineContext: CoroutineContext
        get() = viewModelScope.coroutineContext

    private fun shouldIgnore(throwable: Throwable): Boolean {
        return throwable is SigningCancelledException
    }

    protected inline fun <reified T> SavedStateHandle.getPayload(
        key: String = T::class.java.name,
    ): T {
        return get(key)!!
    }
}

sealed class BaseViewModelEvent {
    class Error(val errorTitle: String) : BaseViewModelEvent()

    class ErrorWithTitle(val title: String, val message: String) : BaseViewModelEvent()

    class Message(val message: String) : BaseViewModelEvent()
}
