package io.paritytech.polkadotapp.feature_coinage_impl.domain.externalPayment.state

import io.paritytech.polkadotapp.common.data.worker.stateMachine.WorkerStateMachineState.TransitionResult
import io.paritytech.polkadotapp.feature_coinage_api.domain.externalPayment.PaymentContext

data class CompletedPaymentState(override val context: PaymentContext) : ExternalPaymentState {
    override val id: String = "Completed"

    context(NoContext)
    override suspend fun performTransition(): TransitionResult<ExternalPaymentState> {
        return TransitionResult.StateTerminal
    }
}

data class FailedPaymentState(override val context: PaymentContext, val reason: String) : ExternalPaymentState {
    override val id: String = "Failed"

    context(NoContext)
    override suspend fun performTransition(): TransitionResult<ExternalPaymentState> {
        return TransitionResult.StateTerminal
    }
}
