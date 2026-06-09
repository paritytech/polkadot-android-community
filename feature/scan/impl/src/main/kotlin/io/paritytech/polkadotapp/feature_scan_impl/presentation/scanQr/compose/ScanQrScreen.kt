package io.paritytech.polkadotapp.feature_scan_impl.presentation.scanQr.compose

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.paritytech.polkadotapp.design.components.dialog.NovaAlertDialog
import io.paritytech.polkadotapp.design.components.icon.NovaIcon
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.Close
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.design.utils.collectAsEffect
import io.paritytech.polkadotapp.feature_scan_impl.presentation.scanQr.ScanQrContract
import io.paritytech.polkadotapp.common.R as RCommon

private val LabelOffsetFromCenter = 250.dp

@Composable
fun ScanQrScreen(contract: ScanQrContract) {
    var permissionGranted by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val owner = LocalLifecycleOwner.current
    LaunchedEffect(permissionGranted, owner) {
        if (permissionGranted) {
            contract.bindToCamera(owner)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val surfaceRequest by contract.surfaceRequest.collectAsStateWithLifecycle()
        surfaceRequest?.let {
            CameraXViewfinder(
                modifier = Modifier.fillMaxSize(),
                surfaceRequest = it
            )
        }

        Image(
            modifier = Modifier.fillMaxSize(),
            imageVector = ImageOverlay,
            contentScale = ContentScale.Crop,
            contentDescription = "image_overlay"
        )

        NovaText(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = LabelOffsetFromCenter)
                .padding(horizontal = PolkadotTheme.spacings.extraLargeIncreased),
            text = stringResource(RCommon.string.scan_point_camera_at_qr_code),
            color = PolkadotTheme.colors.fg.staticWhite,
            style = PolkadotTheme.typography.title.medium,
            textAlign = TextAlign.Center
        )

        IconButton(
            modifier = Modifier.systemBarsPadding(),
            onClick = contract::back
        ) {
            NovaIcon(
                imageVector = NovaIcons.Close,
                tint = Color.White
            )
        }
    }

    var invalidQrAlertIsVisible by remember { mutableStateOf(false) }
    contract.invalidCodeEvent.collectAsEffect { _, _ ->
        invalidQrAlertIsVisible = true
    }

    if (invalidQrAlertIsVisible) {
        NovaAlertDialog(
            title = stringResource(RCommon.string.scan_invalid_code_error_title),
            text = stringResource(RCommon.string.scan_invalid_code_error_message),
            positiveButtonTitle = stringResource(RCommon.string.scan_invalid_code_error_action),
            onPositiveButtonClick = {
                invalidQrAlertIsVisible = false
                contract.invalidationDialogClosed()
            },
            onDismissRequest = {
                invalidQrAlertIsVisible = false
                contract.invalidationDialogClosed()
            }
        )
    }
}
