package io.paritytech.polkadotapp.feature_people_impl.data.personSetup.state

import io.paritytech.polkadotapp.feature_people_impl.data.notifications.BecomeCitizenNotificationPublisher
import io.paritytech.polkadotapp.feature_transactions.api.data.flattenExecutionFailure

class SetPersonalIdAccountState(
    private val stateFactory: PersonSetupStateFactory,
    private val becomeCitizenNotificationPublisher: BecomeCitizenNotificationPublisher
) : PersonSetupNonTerminalState() {
    companion object {
        val ID = "SetPersonalIdAccount"
    }

    override val id = ID

    context(PersonSetupState.Transition)
    override suspend fun performNonTerminalTransition(): Result<PersonSetupState> {
        val personalAccountId = dataSource.candidateAccount.accountIdIn(dataSource.peopleChain)

        return dataSource.setPersonalIdAccount(personalAccountId)
            .flattenExecutionFailure()
            .map {
                becomeCitizenNotificationPublisher.publishBecomeCitizen()
                stateFactory.allDone()
            }
    }
}
