@file:Suppress("HardcodedComposeText")

package io.paritytech.polkadotapp.feature_device_sync_debug_ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.paritytech.polkadotapp.feature_device_sync_api.domain.activeInstallation.ActiveInstallationManager
import io.paritytech.polkadotapp.feature_device_sync_api.domain.activeInstallation.InstallationStatus
import io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer.DeviceTransferFailure
import io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer.DeviceTransferReceiverCoordinator
import io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer.DeviceTransferRequest
import io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer.DeviceTransferRequestListener
import io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer.DeviceTransferSenderCoordinator
import io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer.DeviceTransferState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class DeviceTransferDebugActivity : ComponentActivity() {
    @Inject
    lateinit var receiverCoordinator: DeviceTransferReceiverCoordinator

    @Inject
    lateinit var senderCoordinator: DeviceTransferSenderCoordinator

    @Inject
    lateinit var requestListener: DeviceTransferRequestListener

    @Inject
    lateinit var activeInstallationManager: ActiveInstallationManager

    private val pendingRequest = MutableStateFlow<DeviceTransferRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            requestListener.observePendingRequest().collect {
                pendingRequest.value = it
                Timber.tag(TAG).i("pending request: $it")
            }
        }
        lifecycleScope.launch { activeInstallationManager.status.collect { Timber.tag(TAG).i("installation: $it") } }
        lifecycleScope.launch { receiverCoordinator.state.collect { Timber.tag(TAG).i("receiver: $it") } }
        lifecycleScope.launch { senderCoordinator.state.collect { Timber.tag(TAG).i("sender: $it") } }

        handle(intent)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val status by activeInstallationManager.status.collectAsState()
                    val pending by pendingRequest.collectAsState()
                    val receiverState by receiverCoordinator.state.collectAsState()
                    val senderState by senderCoordinator.state.collectAsState()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Device transfer (debug only)", style = MaterialTheme.typography.titleMedium)

                        when (phoneRole(status, receiverState, senderState)) {
                            PhoneRole.ACTIVE -> {
                                RoleCard(
                                    title = "This phone is ACTIVE",
                                    description = "The account is active on this phone. Send its data from here to the inactive phone.",
                                    color = ACTIVE_PHONE_COLOR,
                                )
                                ActivePhoneSection(pending, senderState, receiverState)
                            }

                            PhoneRole.INACTIVE -> {
                                RoleCard(
                                    title = "This phone is INACTIVE",
                                    description = inactivePhoneDescription(status),
                                    color = INACTIVE_PHONE_COLOR,
                                )
                                InactivePhoneSection(receiverState, senderState)
                            }

                            PhoneRole.UNKNOWN -> {
                                RoleCard(
                                    title = "Checking whether this phone is active",
                                    description = unknownRoleDescription(status),
                                    color = UNKNOWN_ROLE_COLOR,
                                )
                                Text("If this phone is inactive:", fontWeight = FontWeight.Bold)
                                InactivePhoneSection(receiverState, senderState)
                                Text("If this phone is active:", fontWeight = FontWeight.Bold)
                                ActivePhoneSection(pending, senderState, receiverState)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    @Composable
    private fun RoleCard(title: String, description: String, color: Color) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = color, contentColor = Color.White),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    @Composable
    private fun InactivePhoneSection(receiverState: DeviceTransferState, senderState: DeviceTransferState) {
        val isRunning = receiverState.isRunning()

        Text("1. Tap \"Request transfer\" here.\n2. On the active phone, open this screen and tap \"Send data to the inactive phone\".")
        StatusLine(inactivePhoneStatusText(receiverState))
        if (senderState.isFinished()) {
            Text("Earlier this phone sent its data: ${activePhoneStatusText(senderState)}.")
        }
        Button(modifier = Modifier.fillMaxWidth(), enabled = !isRunning, onClick = { receiverCoordinator.start() }) {
            Text("Request transfer")
        }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = isRunning, onClick = { receiverCoordinator.cancel() }) {
            Text("Cancel")
        }
    }

    @Composable
    private fun ActivePhoneSection(
        pending: DeviceTransferRequest?,
        senderState: DeviceTransferState,
        receiverState: DeviceTransferState,
    ) {
        val isRunning = senderState.isRunning()
        val canAnswer = pending != null && !isRunning

        Text(
            pending?.let { "An inactive phone asks for the data: ${it.deviceModel}, ${it.operatingSystem}." }
                ?: "No request yet. On the inactive phone, tap \"Request transfer\"."
        )
        StatusLine(activePhoneStatusText(senderState))
        if (receiverState.isFinished()) {
            Text("Earlier this phone received data: ${inactivePhoneStatusText(receiverState)}.")
        }
        Button(modifier = Modifier.fillMaxWidth(), enabled = canAnswer, onClick = { pending?.let(senderCoordinator::confirm) }) {
            Text("Send data to the inactive phone")
        }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = canAnswer, onClick = { pending?.let(senderCoordinator::reject) }) {
            Text("Decline the request")
        }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = isRunning, onClick = { senderCoordinator.cancel() }) {
            Text("Stop sending")
        }
    }

    @Composable
    private fun StatusLine(text: String) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Status: $text",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }
    }

    private fun handle(intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        Timber.tag(TAG).i("action: $action")

        when (action) {
            "start" -> receiverCoordinator.start()
            "confirm" -> pendingRequest.value?.let(senderCoordinator::confirm) ?: Timber.tag(TAG).w("no pending request to confirm")
            "reject" -> pendingRequest.value?.let(senderCoordinator::reject) ?: Timber.tag(TAG).w("no pending request to reject")
            "cancel" -> {
                receiverCoordinator.cancel()
                senderCoordinator.cancel()
            }
        }
    }

    private enum class PhoneRole { ACTIVE, INACTIVE, UNKNOWN }

    private fun phoneRole(
        status: InstallationStatus,
        receiverState: DeviceTransferState,
        senderState: DeviceTransferState,
    ): PhoneRole = when {
        senderState.isRunning() -> PhoneRole.ACTIVE
        receiverState.isRunning() -> PhoneRole.INACTIVE
        status == InstallationStatus.Active -> PhoneRole.ACTIVE
        status is InstallationStatus.Inactive -> PhoneRole.INACTIVE
        else -> PhoneRole.UNKNOWN
    }

    private fun inactivePhoneDescription(status: InstallationStatus): String = when (status) {
        is InstallationStatus.Inactive -> with(status.activeInstallation) {
            "The account is active on another phone: $deviceModel, $operatingSystem. Get the data from it to this phone."
        }

        InstallationStatus.Active -> "This phone is active now."
        else -> "Get the data from the active phone to this phone."
    }

    private fun unknownRoleDescription(status: InstallationStatus): String = when (status) {
        InstallationStatus.Unreadable -> "Could not read which phone is active for this account."
        else -> "Not known yet which phone is active for this account. Wait a few seconds."
    }

    private fun inactivePhoneStatusText(state: DeviceTransferState): String = when (state) {
        DeviceTransferState.Idle -> "not started"
        DeviceTransferState.WaitingForSender -> "waiting for the active phone to tap \"Send data to the inactive phone\""
        DeviceTransferState.Connecting -> "connecting to the active phone"
        DeviceTransferState.Transferring -> "receiving data"
        is DeviceTransferState.Syncing -> "receiving data: ${state.appliedChanges} of ${state.totalChanges} changes"
        is DeviceTransferState.Files -> "receiving files: ${state.receivedBytes.inWholeKilobytes} of ${state.totalBytes.inWholeKilobytes} KB"
        DeviceTransferState.Finalizing -> "making this phone active"
        DeviceTransferState.Completed -> "done, this phone is active now"
        is DeviceTransferState.Failed -> "failed, ${state.reason.text()}"
    }

    private fun activePhoneStatusText(state: DeviceTransferState): String = when (state) {
        DeviceTransferState.Idle -> "not started"
        DeviceTransferState.Connecting -> "connecting to the inactive phone"
        DeviceTransferState.Transferring,
        is DeviceTransferState.Syncing,
        is DeviceTransferState.Files -> "sending data to the inactive phone"

        DeviceTransferState.WaitingForSender,
        DeviceTransferState.Finalizing -> "finishing"

        DeviceTransferState.Completed -> "done, the other phone is active now and this one is inactive"
        is DeviceTransferState.Failed -> "failed, ${state.reason.text()}"
    }

    private fun DeviceTransferFailure.text(): String = when (this) {
        DeviceTransferFailure.EXPIRED -> "the request is too old, request the transfer again on the inactive phone"
        DeviceTransferFailure.REJECTED -> "the active phone declined the request"
        DeviceTransferFailure.CANCELLED -> "cancelled"
        DeviceTransferFailure.ABORTED -> "the other phone stopped the transfer"
        DeviceTransferFailure.TIMED_OUT -> "the other phone did not answer in time"
        DeviceTransferFailure.FAILED -> "error, see logcat"
    }

    private fun DeviceTransferState.isFinished(): Boolean =
        this == DeviceTransferState.Completed || this is DeviceTransferState.Failed

    private fun DeviceTransferState.isRunning(): Boolean = when (this) {
        DeviceTransferState.Idle,
        DeviceTransferState.Completed,
        is DeviceTransferState.Failed -> false

        else -> true
    }

    private companion object {
        const val TAG = "DeviceTransferDebug"
        const val EXTRA_ACTION = "action"

        val ACTIVE_PHONE_COLOR = Color(0xFFB45309)
        val INACTIVE_PHONE_COLOR = Color(0xFF1D4ED8)
        val UNKNOWN_ROLE_COLOR = Color(0xFF4B5563)
    }
}
